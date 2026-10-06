package com.example.invoice.service;

import nu.pattern.OpenCV;

final class OpenCvRuntime {
	private static volatile boolean loaded;
	private OpenCvRuntime() {}
	static synchronized void load() {
		if (!loaded) {
			OpenCV.loadLocally();
			loaded = true;
		}
	}
}
