# Reviewed immutable JVM profile base plus a separately prepared SDK image.
# No SDK packages are downloaded and no license prompt is auto-accepted here.
ARG DUA_JVM_BASE
ARG ANDROID_SDK_IMAGE
FROM ${ANDROID_SDK_IMAGE} AS sdk
FROM ${DUA_JVM_BASE}
ARG DUA_JVM_BASE
ARG ANDROID_SDK_IMAGE
ARG ANDROID_SDK_SOURCE=/opt/android-sdk
USER root
COPY --from=sdk ${ANDROID_SDK_SOURCE}/ /opt/android-sdk/
# Optional reviewed public dependency seed; the repository's default is empty.
COPY gradle-seed/ /opt/dua-gradle-seed/
ENV ANDROID_HOME=/opt/android-sdk ANDROID_SDK_ROOT=/opt/android-sdk
RUN /usr/local/bin/python -I -S /opt/dua-verify-profile.py android "$DUA_JVM_BASE" "$ANDROID_SDK_IMAGE"
LABEL dev.dua.coding.profile=android
USER 10001:10001
WORKDIR /workspace
CMD ["java", "--version"]
