import hashlib
from pathlib import Path
from .checkpoint_manager import digest


def classify_duplicates(samples):
    """First manifest entry per SHA-256 is canonical; never compare predictions or filenames."""
    seen = {}
    result = []
    for sample in samples:
        canonical = seen.setdefault(sample['file_hash'], sample['sample_id'])
        duplicate_of = canonical if canonical != sample['sample_id'] else None
        result.append(dict(sample, duplicate_of=duplicate_of,
                           dataset_status='SKIPPED_DUPLICATE' if duplicate_of else 'READY'))
    return result


def load_dataset(folder):
    root = Path(folder).resolve()
    if not root.is_dir():
        raise ValueError('Dataset directory does not exist')
    samples = []
    for path in sorted(root.rglob('*'), key=lambda p:p.relative_to(root).as_posix()):
        if path.is_symlink() or not path.is_file() or path.suffix.lower() not in {'.jpg', '.jpeg', '.png', '.pdf'}:
            continue
        if not path.resolve().is_relative_to(root):
            raise ValueError('Dataset path escapes root')
        with path.open('rb') as source:
            file_hash = hashlib.file_digest(source, 'sha256').hexdigest()
        relative = path.relative_to(root).as_posix()
        samples.append(dict(sample_id='S_' + digest([relative, file_hash])[:24],
                            original_filename=path.name, relative_path=relative, file_hash=file_hash))
    if not samples:
        raise ValueError('Dataset contains no JPG/PNG/PDF')
    return classify_duplicates(samples)
