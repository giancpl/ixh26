FROM python:3.11-slim-bookworm
WORKDIR /app
COPY pyproject.toml ./
COPY requirements-social.lock ./
COPY settlement ./settlement
COPY upp_protocol ./upp_protocol
COPY social ./social
RUN --mount=type=cache,target=/root/.cache/pip pip install -c requirements-social.lock '.[social,test]'
COPY contracts ./contracts
COPY tests ./tests
COPY scripts ./scripts
COPY docker/regtest/client.conf /config/client.conf
COPY README.md ./
ENV PYTHONUNBUFFERED=1
CMD ["python", "-m", "uvicorn", "social.api:app", "--host", "0.0.0.0", "--port", "8080", "--no-access-log"]
