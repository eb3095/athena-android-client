IMAGE_NAME := athena-android-builder
GRADLE_CACHE := $(HOME)/.gradle-docker-cache

.PHONY: clean build debug release install uninstall reinstall icons docker-build docker-clean check-config docker-image

build: docker-build

debug: docker-build

check-config:
	@if [ ! -f local.properties ]; then \
		echo "Error: local.properties not found."; \
		echo "Create local.properties with:"; \
		echo "  api.url=https://your-athena-server.com"; \
		echo "  api.token=your-auth-token"; \
		exit 1; \
	fi

docker-image:
	@mkdir -p $(GRADLE_CACHE)
	@if ! docker image inspect $(IMAGE_NAME) >/dev/null 2>&1; then \
		echo "Building Docker image (first time)..."; \
		docker build --platform linux/amd64 -t $(IMAGE_NAME) .; \
	fi

docker-rebuild:
	@echo "Rebuilding Docker image..."
	@mkdir -p $(GRADLE_CACHE)
	docker build --platform linux/amd64 -t $(IMAGE_NAME) .

docker-build: check-config docker-image
	@echo "Building Android app..."
	docker run --rm --platform linux/amd64 --network host \
		-v "$(CURDIR)/app:/app/app" \
		-v "$(GRADLE_CACHE):/root/.gradle" \
		$(IMAGE_NAME)
	@echo "APK available at: app/build/outputs/apk/debug/app-debug.apk"

release: check-config docker-image
	@echo "Building release APK..."
	docker run --rm --platform linux/amd64 --network host \
		-v "$(CURDIR)/app:/app/app" \
		-v "$(GRADLE_CACHE):/root/.gradle" \
		$(IMAGE_NAME) ./gradlew assembleRelease
	@echo "APK available at: app/build/outputs/apk/release/app-release-unsigned.apk"

clean:
	rm -rf app/build
	rm -rf build
	rm -rf .gradle

docker-clean:
	docker rmi $(IMAGE_NAME) 2>/dev/null || true
	rm -rf $(GRADLE_CACHE)

install:
	adb install app/build/outputs/apk/debug/app-debug.apk

uninstall:
	adb shell am force-stop com.athena.client
	adb uninstall com.athena.client

reinstall: uninstall install

icons:
	@echo "Generating app icons from image.png..."
	@mkdir -p app/src/main/res/mipmap-mdpi
	@mkdir -p app/src/main/res/mipmap-hdpi
	@mkdir -p app/src/main/res/mipmap-xhdpi
	@mkdir -p app/src/main/res/mipmap-xxhdpi
	@mkdir -p app/src/main/res/mipmap-xxxhdpi
	@# Legacy launcher icons (for older Android)
	@sips -z 48 48 image.png --out app/src/main/res/mipmap-mdpi/ic_launcher.png 2>/dev/null || \
		convert image.png -resize 48x48 app/src/main/res/mipmap-mdpi/ic_launcher.png
	@sips -z 72 72 image.png --out app/src/main/res/mipmap-hdpi/ic_launcher.png 2>/dev/null || \
		convert image.png -resize 72x72 app/src/main/res/mipmap-hdpi/ic_launcher.png
	@sips -z 96 96 image.png --out app/src/main/res/mipmap-xhdpi/ic_launcher.png 2>/dev/null || \
		convert image.png -resize 96x96 app/src/main/res/mipmap-xhdpi/ic_launcher.png
	@sips -z 144 144 image.png --out app/src/main/res/mipmap-xxhdpi/ic_launcher.png 2>/dev/null || \
		convert image.png -resize 144x144 app/src/main/res/mipmap-xxhdpi/ic_launcher.png
	@sips -z 192 192 image.png --out app/src/main/res/mipmap-xxxhdpi/ic_launcher.png 2>/dev/null || \
		convert image.png -resize 192x192 app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
	@# Adaptive icon foreground
	@sips -z 108 108 image.png --out app/src/main/res/mipmap-mdpi/ic_launcher_foreground.png 2>/dev/null
	@sips -z 162 162 image.png --out app/src/main/res/mipmap-hdpi/ic_launcher_foreground.png 2>/dev/null
	@sips -z 216 216 image.png --out app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.png 2>/dev/null
	@sips -z 324 324 image.png --out app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.png 2>/dev/null
	@sips -z 432 432 image.png --out app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.png 2>/dev/null
	@echo "Icons generated successfully."
