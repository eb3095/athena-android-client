IMAGE_NAME := athena-android-builder

.PHONY: clean build debug release install uninstall icons docker-build docker-clean check-config

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

docker-build: check-config
	@echo "Building Android app with Docker..."
	docker build --platform linux/amd64 -t $(IMAGE_NAME) .
	docker run --rm --platform linux/amd64 --network host -v "$(CURDIR)/app/build:/app/app/build" $(IMAGE_NAME)
	@echo "APK available at: app/build/outputs/apk/debug/app-debug.apk"

release: check-config
	@echo "Building release APK with Docker..."
	docker build --platform linux/amd64 -t $(IMAGE_NAME) .
	docker run --rm --platform linux/amd64 --network host -v "$(CURDIR)/app/build:/app/app/build" $(IMAGE_NAME) ./gradlew assembleRelease
	@echo "APK available at: app/build/outputs/apk/release/app-release-unsigned.apk"

clean:
	rm -rf app/build
	rm -rf build
	rm -rf .gradle

docker-clean:
	docker rmi $(IMAGE_NAME) 2>/dev/null || true

install:
	adb install -r app/build/outputs/apk/debug/app-debug.apk

uninstall:
	adb uninstall com.athena.client || true

icons:
	@echo "Generating app icons from logo.png..."
	@mkdir -p app/src/main/res/mipmap-mdpi
	@mkdir -p app/src/main/res/mipmap-hdpi
	@mkdir -p app/src/main/res/mipmap-xhdpi
	@mkdir -p app/src/main/res/mipmap-xxhdpi
	@mkdir -p app/src/main/res/mipmap-xxxhdpi
	@sips -z 48 48 logo.png --out app/src/main/res/mipmap-mdpi/ic_launcher.png 2>/dev/null || \
		convert logo.png -resize 48x48 app/src/main/res/mipmap-mdpi/ic_launcher.png
	@sips -z 72 72 logo.png --out app/src/main/res/mipmap-hdpi/ic_launcher.png 2>/dev/null || \
		convert logo.png -resize 72x72 app/src/main/res/mipmap-hdpi/ic_launcher.png
	@sips -z 96 96 logo.png --out app/src/main/res/mipmap-xhdpi/ic_launcher.png 2>/dev/null || \
		convert logo.png -resize 96x96 app/src/main/res/mipmap-xhdpi/ic_launcher.png
	@sips -z 144 144 logo.png --out app/src/main/res/mipmap-xxhdpi/ic_launcher.png 2>/dev/null || \
		convert logo.png -resize 144x144 app/src/main/res/mipmap-xxhdpi/ic_launcher.png
	@sips -z 192 192 logo.png --out app/src/main/res/mipmap-xxxhdpi/ic_launcher.png 2>/dev/null || \
		convert logo.png -resize 192x192 app/src/main/res/mipmap-xxxhdpi/ic_launcher.png
	@echo "Icons generated successfully."
