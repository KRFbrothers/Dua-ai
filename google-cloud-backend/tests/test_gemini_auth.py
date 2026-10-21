"""GEMINI_AUTH=adc|apikey: Vertex AI express mode with GOOGLE_API_KEY; ADC stays the default and rollback."""
import logging
import os
import sys
import types as pytypes

import pytest

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import agent_models  # noqa: E402
import app as engine  # noqa: E402

KEY = "test-key-not-real"


class FakeClient:
    def __init__(self, **kwargs):
        self.kwargs = kwargs


@pytest.fixture
def fake_genai(monkeypatch):
    """Records genai.Client(...) kwargs; works without google-genai installed (light CI)."""
    genai = pytypes.ModuleType("google.genai")
    genai.Client = FakeClient
    google = sys.modules.get("google") or pytypes.ModuleType("google")
    monkeypatch.setitem(sys.modules, "google", google)
    monkeypatch.setattr(google, "genai", genai, raising=False)
    monkeypatch.setitem(sys.modules, "google.genai", genai)
    monkeypatch.setattr(engine, "_client", None)
    monkeypatch.setattr(engine, "_adc_client", None)
    monkeypatch.setattr(agent_models, "_key_client", None)
    monkeypatch.setattr(agent_models, "_warned", [])
    monkeypatch.setattr(engine, "PROJECT_ID", "proj-test")
    monkeypatch.delenv("GEMINI_AUTH", raising=False)
    monkeypatch.delenv("GOOGLE_API_KEY", raising=False)
    return monkeypatch


def test_default_is_adc(fake_genai):
    assert agent_models.key_mode() is False
    client = engine.get_client()
    assert client.kwargs == {"vertexai": True, "project": "proj-test", "location": engine.LOCATION}
    assert engine.get_adc_client() is client


def test_apikey_client_uses_key_and_no_project(fake_genai):
    fake_genai.setenv("GEMINI_AUTH", "apikey")
    fake_genai.setenv("GOOGLE_API_KEY", KEY)
    fake_genai.setattr(engine, "PROJECT_ID", "")  # project is not required for the key client
    client = engine.get_client()
    assert client.kwargs == {"vertexai": True, "api_key": KEY, "project": None, "location": None}


def test_missing_key_warns_and_falls_back_to_adc(fake_genai, caplog):
    fake_genai.setenv("GEMINI_AUTH", "apikey")
    with caplog.at_level(logging.WARNING):
        client = engine.get_client()
        agent_models.key_mode()
    assert client.kwargs["project"] == "proj-test" and "api_key" not in client.kwargs
    warnings = [r.getMessage() for r in caplog.records if "GOOGLE_API_KEY" in r.getMessage()]
    assert len(warnings) == 1 and "using ADC" in warnings[0]


def test_key_never_logged(fake_genai, caplog):
    fake_genai.setenv("GEMINI_AUTH", "apikey")
    fake_genai.setenv("GOOGLE_API_KEY", KEY)
    with caplog.at_level(logging.DEBUG):
        engine.get_client()
    assert KEY not in caplog.text


def test_imagine_and_embeddings_stay_on_adc(fake_genai):
    fake_genai.setenv("GEMINI_AUTH", "apikey")
    fake_genai.setenv("GOOGLE_API_KEY", KEY)
    adc = engine.get_adc_client()
    assert adc.kwargs == {"vertexai": True, "project": "proj-test", "location": engine.LOCATION}
    assert engine.get_client() is not adc
    source = open(engine.__file__, encoding="utf-8").read()
    assert "VertexEmbeddings(get_client()" not in source
    assert "imagine.handle(body, owner[\"token\"], owner[\"id\"], get_adc_client())" in source
    assert "token, user_id, get_adc_client())" in source


def test_with_key_is_noop_in_adc_mode(fake_genai):
    assert agent_models.with_key("gemini-4-argon") == "gemini-4-argon"


@pytest.mark.parametrize("retry", ["1", "0"])
def test_adk_model_gets_key_client(monkeypatch, retry):
    pytest.importorskip("google.adk")
    from google.adk.models import Gemini
    monkeypatch.setattr(agent_models, "_warned", [])
    monkeypatch.delenv("ADK_MODELS_JSON", raising=False)
    monkeypatch.setenv("ADK_GEMINI_RETRY", retry)
    monkeypatch.setenv("GOOGLE_CLOUD_PROJECT", "proj-test")
    monkeypatch.setenv("GEMINI_AUTH", "apikey")
    monkeypatch.setenv("GOOGLE_API_KEY", KEY)
    model = agent_models.resolve("controller", "gemini-4-argon")
    assert isinstance(model, Gemini) and model.model == "gemini-4-argon"
    api = model.api_client._api_client
    assert api.vertexai and api.api_key == KEY and api.project is None  # key client, not ADC
    assert (model.retry_options is not None) == (retry == "1")  # ADK_GEMINI_RETRY kept


def test_adk_model_stays_adc_by_default(monkeypatch):
    pytest.importorskip("google.adk")
    monkeypatch.delenv("ADK_MODELS_JSON", raising=False)
    monkeypatch.delenv("GEMINI_AUTH", raising=False)
    monkeypatch.setenv("ADK_GEMINI_RETRY", "1")
    model = agent_models.resolve("controller", "gemini-4-argon")
    assert getattr(model, "client_kwargs", None) is None or "api_key" not in model.client_kwargs
    monkeypatch.setenv("ADK_GEMINI_RETRY", "0")
    assert agent_models.resolve("controller", "gemini-4-argon") == "gemini-4-argon"
