# Supply a reviewed immutable upstream image with /opt/java/openjdk and /opt/gradle.
# A multi-stage copy avoids inheriting upstream users, env, entrypoints or VOLUMEs.
ARG JVM_TOOLCHAIN_IMAGE
FROM ${JVM_TOOLCHAIN_IMAGE} AS toolchain
FROM python:3.12-slim@sha256:a6e34c598f2467ed0e9a8d349809fcd8b5c603269512df273a0bb1784edc11b1
ARG JVM_TOOLCHAIN_IMAGE
COPY --from=toolchain /opt/java/openjdk/ /opt/java/openjdk/
COPY --from=toolchain /opt/gradle/ /opt/gradle/
COPY gradle-seed/ /opt/dua-gradle-seed/
COPY dua-gradle /usr/local/bin/dua-gradle
COPY verify_profile_image.py /opt/dua-verify-profile.py
ENV JAVA_HOME=/opt/java/openjdk GRADLE_HOME=/opt/gradle \
    PATH=/opt/java/openjdk/bin:/opt/gradle/bin:/usr/local/bin:/usr/bin:/bin \
    HOME=/tmp/home XDG_CACHE_HOME=/tmp/cache GRADLE_USER_HOME=/cache/gradle \
    PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
RUN /usr/local/bin/python -I -S /opt/dua-verify-profile.py jvm "$JVM_TOOLCHAIN_IMAGE" \
    && chmod 0755 /usr/local/bin/dua-gradle \
    && groupadd --gid 10001 dua-sandbox \
    && useradd --uid 10001 --gid dua-sandbox --no-create-home dua-sandbox
LABEL dev.dua.coding.profile=jvm
USER 10001:10001
WORKDIR /workspace
CMD ["java", "--version"]
