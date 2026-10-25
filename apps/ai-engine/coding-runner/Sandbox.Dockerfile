# Reference Python-only sandbox. Bake project dependencies into a reviewed image
# before use; runtime networking is disabled. Never bake credentials into it.
FROM python:3.12-slim@sha256:a6e34c598f2467ed0e9a8d349809fcd8b5c603269512df273a0bb1784edc11b1
RUN groupadd --gid 10001 dua-sandbox && useradd --uid 10001 --gid dua-sandbox --no-create-home dua-sandbox
ENV HOME=/tmp/home XDG_CACHE_HOME=/tmp/cache PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
LABEL dev.dua.coding.profile=python
USER 10001:10001
WORKDIR /workspace
# The controller overrides ENTRYPOINT with the independent hard deadline.
CMD ["python", "--version"]
