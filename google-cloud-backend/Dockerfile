# syntax=docker/dockerfile:1
FROM python:3.12-slim@sha256:a6e34c598f2467ed0e9a8d349809fcd8b5c603269512df273a0bb1784edc11b1
ARG DUA_BUILD_SHA=development
ARG REQUIREMENTS=requirements-adapters-lock.txt
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PORT=8080 DUA_BUILD_SHA=$DUA_BUILD_SHA \
    ADK_CAPTURE_MESSAGE_CONTENT_IN_SPANS=false OTEL_INSTRUMENTATION_GENAI_CAPTURE_MESSAGE_CONTENT=""
WORKDIR /app
COPY requirements*.txt ./
# The optional CA mount supports managed build proxies without retaining the CA.
RUN --mount=type=secret,id=proxy_ca \
    if [ -f /run/secrets/proxy_ca ]; then export PIP_CERT=/run/secrets/proxy_ca; fi; \
    python -m pip install --no-cache-dir -r "$REQUIREMENTS" && python -m pip check
RUN groupadd --gid 10001 dua && useradd --uid 10001 --gid dua --no-create-home dua
COPY --chown=dua:dua *.py ./
COPY --chown=dua:dua adk_examples/ ./adk_examples/
COPY --chown=dua:dua evals/ ./evals/
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8080/', timeout=3)"
CMD ["python", "adk_server.py"]
