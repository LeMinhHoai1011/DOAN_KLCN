package com.example.invoice.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Deterministically reconstructs reading lines and local blocks from immutable OCR word boxes. */
@Component
public class OcrLayoutReconstructor {
	private static final Logger log = LoggerFactory.getLogger(OcrLayoutReconstructor.class);
	private static final double MIN_VERTICAL_OVERLAP = 0.45d;
	private static final double MAX_CENTER_DISTANCE_HEIGHTS = 0.65d;
	private static final double MAX_BLOCK_HEIGHT = 0.28d;

	public OcrPageResult reconstruct(OcrPageResult page) {
		if (page == null || page.words().isEmpty()) return page;
		List<WordBox> valid = page.words().stream().map(WordBox::from).filter(Objects::nonNull).toList();
		if (valid.size() != page.words().size())
			log.warn("OCR layout page={} skippedInvalidWords={}", page.pageNumber(), page.words().size() - valid.size());
		if (valid.isEmpty()) return withLayout(page, List.of(), List.of());
		double medianWordHeight = median(valid.stream().map(WordBox::height).toList());
		List<Row> rows = groupRows(valid, medianWordHeight);
		List<OcrLine> lines = createLines(page.pageNumber(), rows, medianWordHeight);
		List<OcrBlock> blocks = createBlocks(page.pageNumber(), lines);
		return withLayout(page, lines, blocks);
	}

	/** Compact reading context for the LLM. Coordinates deliberately stay out of the prompt. */
	public String structuredContext(OcrPageResult page) {
		if (page == null) return "";
		if (page.blocks().isEmpty() || page.lines().isEmpty()) return page.text() == null ? "" : page.text();
		var linesById = page.lines().stream().collect(Collectors.toMap(OcrLine::id, line -> line));
		StringBuilder value = new StringBuilder("[PAGE ").append(page.pageNumber()).append("]");
		for (OcrBlock block : page.blocks()) {
			value.append("\n[BLOCK ").append(block.id()).append(']');
			for (String lineId : block.lineIds()) {
				OcrLine line = linesById.get(lineId);
				if (line != null) value.append("\n[").append(line.id()).append("] ").append(line.text());
			}
		}
		return value.toString();
	}

	private List<Row> groupRows(List<WordBox> words, double medianHeight) {
		List<WordBox> ordered = words.stream().sorted(Comparator.comparingDouble(WordBox::centerY)
				.thenComparingDouble(WordBox::x).thenComparing(box -> box.word().id())).toList();
		List<Row> rows = new ArrayList<>();
		for (WordBox word : ordered) {
			Row best = null;
			double bestDistance = Double.MAX_VALUE;
			for (int index = rows.size() - 1; index >= 0; index--) {
				Row candidate = rows.get(index);
				if (word.y() - candidate.bottom() > medianHeight * 2.0d) break;
				double distance = Math.abs(word.centerY() - candidate.centerY());
				double allowed = Math.max(medianHeight, Math.max(word.height(), candidate.averageHeight()))
						* MAX_CENTER_DISTANCE_HEIGHTS;
				if (verticalOverlap(word.y(), word.bottom(), candidate.top(), candidate.bottom()) >= MIN_VERTICAL_OVERLAP
						|| distance <= allowed) {
					if (distance < bestDistance) { best = candidate; bestDistance = distance; }
				}
			}
			if (best == null) { best = new Row(); rows.add(best); }
			best.add(word);
		}
		return rows.stream().sorted(Comparator.comparingDouble(Row::top).thenComparingDouble(Row::left)).toList();
	}

	private List<OcrLine> createLines(int pageNumber, List<Row> rows, double medianHeight) {
		List<List<WordBox>> segments = new ArrayList<>();
		for (Row row : rows) {
			List<WordBox> ordered = row.words.stream().sorted(Comparator.comparingDouble(WordBox::x)).toList();
			List<WordBox> segment = new ArrayList<>();
			for (WordBox word : ordered) {
				if (!segment.isEmpty()) {
					WordBox previous = segment.getLast();
					double gap = word.x() - previous.right();
					double localHeight = Math.max(medianHeight, Math.max(previous.height(), word.height()));
					if (gap > Math.max(0.08d, localHeight * 4.5d)) {
						segments.add(List.copyOf(segment)); segment.clear();
					}
				}
				segment.add(word);
			}
			if (!segment.isEmpty()) segments.add(List.copyOf(segment));
		}
		segments.sort(Comparator.comparingDouble((List<WordBox> words) -> bounds(words).y)
				.thenComparingDouble(words -> bounds(words).x));
		List<OcrLine> lines = new ArrayList<>();
		for (int index = 0; index < segments.size(); index++) {
			List<WordBox> segment = segments.get(index); Bounds bounds = bounds(segment);
			lines.add(new OcrLine("p%d-l%d".formatted(pageNumber, index + 1), pageNumber,
					segment.stream().map(box -> box.word().text().trim()).collect(Collectors.joining(" ")),
					weightedConfidence(segment), bounds.x, bounds.y, bounds.width, bounds.height,
					segment.stream().map(box -> box.word().id()).toList()));
		}
		return List.copyOf(lines);
	}

	private List<OcrBlock> createBlocks(int pageNumber, List<OcrLine> lines) {
		if (lines.isEmpty()) return List.of();
		double medianLineHeight = median(lines.stream().map(OcrLine::height).toList());
		List<List<OcrLine>> groups = new ArrayList<>();
		List<OcrLine> current = new ArrayList<>();
		for (OcrLine line : lines) {
			if (!current.isEmpty() && !belongsToBlock(current, line, medianLineHeight)) {
				groups.add(List.copyOf(current)); current.clear();
			}
			current.add(line);
		}
		if (!current.isEmpty()) groups.add(List.copyOf(current));
		List<OcrBlock> blocks = new ArrayList<>();
		for (int index = 0; index < groups.size(); index++) {
			List<OcrLine> group = groups.get(index); Bounds box = lineBounds(group);
			blocks.add(new OcrBlock("p%d-b%d".formatted(pageNumber, index + 1), pageNumber,
					group.stream().map(OcrLine::text).collect(Collectors.joining("\n")), weightedLineConfidence(group),
					box.x, box.y, box.width, box.height, group.stream().map(OcrLine::id).toList()));
		}
		return List.copyOf(blocks);
	}

	private boolean belongsToBlock(List<OcrLine> current, OcrLine next, double medianHeight) {
		Bounds block = lineBounds(current); OcrLine previous = current.getLast();
		double gap = next.y() - (previous.y() + previous.height());
		double overlap = horizontalOverlap(block.x, block.right(), next.x(), next.x() + next.width());
		double leftDelta = Math.abs(next.x() - previous.x());
		boolean aligned = leftDelta <= Math.max(0.045d, medianHeight * 3.5d) || overlap >= 0.20d;
		double resultingBottom = Math.max(block.bottom(), next.y() + next.height());
		return gap <= Math.max(0.025d, medianHeight * 1.8d) && gap >= -medianHeight
				&& aligned && resultingBottom - block.y <= MAX_BLOCK_HEIGHT;
	}

	private OcrPageResult withLayout(OcrPageResult page, List<OcrLine> lines, List<OcrBlock> blocks) {
		return new OcrPageResult(page.pageNumber(), page.imageWidth(), page.imageHeight(), page.text(), page.confidence(),
				page.words(), page.durationMs(), page.warning(), page.language(), lines, blocks);
	}

	private static Bounds bounds(List<WordBox> words) {
		double left = words.stream().mapToDouble(WordBox::x).min().orElse(0);
		double top = words.stream().mapToDouble(WordBox::y).min().orElse(0);
		double right = words.stream().mapToDouble(WordBox::right).max().orElse(left);
		double bottom = words.stream().mapToDouble(WordBox::bottom).max().orElse(top);
		return new Bounds(clamp(left), clamp(top), clamp(right) - clamp(left), clamp(bottom) - clamp(top));
	}

	private static Bounds lineBounds(List<OcrLine> lines) {
		double left = lines.stream().mapToDouble(OcrLine::x).min().orElse(0);
		double top = lines.stream().mapToDouble(OcrLine::y).min().orElse(0);
		double right = lines.stream().mapToDouble(line -> line.x() + line.width()).max().orElse(left);
		double bottom = lines.stream().mapToDouble(line -> line.y() + line.height()).max().orElse(top);
		return new Bounds(clamp(left), clamp(top), clamp(right) - clamp(left), clamp(bottom) - clamp(top));
	}

	private static float weightedConfidence(List<WordBox> words) {
		double weight = 0, total = 0;
		for (WordBox box : words) { int length = textWeight(box.word().text()); weight += length; total += box.word().confidence() * length; }
		return weight == 0 ? 0 : (float) (total / weight);
	}

	private static float weightedLineConfidence(List<OcrLine> lines) {
		double weight = 0, total = 0;
		for (OcrLine line : lines) { int length = textWeight(line.text()); weight += length; total += line.confidence() * length; }
		return weight == 0 ? 0 : (float) (total / weight);
	}

	private static int textWeight(String text) { return Math.max(1, text == null ? 0 : text.codePointCount(0, text.length())); }
	private static double median(List<Double> values) {
		List<Double> sorted = values.stream().filter(value -> value > 0 && Double.isFinite(value)).sorted().toList();
		if (sorted.isEmpty()) return 0.02d; int middle = sorted.size() / 2;
		return sorted.size() % 2 == 1 ? sorted.get(middle) : (sorted.get(middle - 1) + sorted.get(middle)) / 2d;
	}
	private static double verticalOverlap(double top1, double bottom1, double top2, double bottom2) {
		double overlap = Math.max(0, Math.min(bottom1, bottom2) - Math.max(top1, top2));
		return overlap / Math.max(0.000001d, Math.min(bottom1 - top1, bottom2 - top2));
	}
	private static double horizontalOverlap(double left1, double right1, double left2, double right2) {
		double overlap = Math.max(0, Math.min(right1, right2) - Math.max(left1, left2));
		return overlap / Math.max(0.000001d, Math.min(right1 - left1, right2 - left2));
	}
	private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }

	private static final class Row {
		private final List<WordBox> words = new ArrayList<>();
		void add(WordBox word) { words.add(word); }
		double top() { return words.stream().mapToDouble(WordBox::y).min().orElse(0); }
		double bottom() { return words.stream().mapToDouble(WordBox::bottom).max().orElse(0); }
		double left() { return words.stream().mapToDouble(WordBox::x).min().orElse(0); }
		double centerY() { return (top() + bottom()) / 2d; }
		double averageHeight() { return words.stream().mapToDouble(WordBox::height).average().orElse(0); }
	}

	private record WordBox(OcrWord word, double x, double y, double width, double height) {
		static WordBox from(OcrWord word) {
			if (word == null || word.text() == null || word.text().isBlank() || word.id() == null
					|| !finite(word.x(), word.y(), word.width(), word.height()) || word.width() <= 0 || word.height() <= 0) return null;
			double left = clamp(word.x()), top = clamp(word.y());
			double right = clamp(word.x() + word.width()), bottom = clamp(word.y() + word.height());
			return right <= left || bottom <= top ? null : new WordBox(word, left, top, right - left, bottom - top);
		}
		double right() { return x + width; }
		double bottom() { return y + height; }
		double centerY() { return y + height / 2d; }
		private static boolean finite(double... values) { for (double value : values) if (!Double.isFinite(value)) return false; return true; }
	}
	private record Bounds(double x, double y, double width, double height) {
		double right() { return x + width; } double bottom() { return y + height; }
	}
}
