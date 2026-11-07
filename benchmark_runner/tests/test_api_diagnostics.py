import sys
from pathlib import Path
import pytest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
from src.api_client import ApiClient, ApiError


@pytest.mark.parametrize('token',['<JWT của tài khoản ADMIN>','<paste-token>','abc\r\nsecret',' ','token có dấu'])
def test_placeholder_and_bad_headers_rejected_without_exposing_token(monkeypatch,token):
    monkeypatch.setenv('BENCHMARK_JWT',token)
    with pytest.raises(ApiError,match='^JWT_INVALID_VALUE$'):
        ApiClient({'api_url':'http://localhost:8081'})


def test_missing_token(monkeypatch):
    monkeypatch.delenv('BENCHMARK_JWT',raising=False)
    with pytest.raises(ApiError,match='JWT_MISSING'):ApiClient({'api_url':'http://localhost:8081'})


def test_http_and_non_json_errors_are_distinct(monkeypatch):
    monkeypatch.setenv('BENCHMARK_JWT','Bearer test-only')
    client=ApiClient({'api_url':'http://localhost:8081'})
    assert client.session.headers['Authorization']=='Bearer test-only'
    class Response:
        ok=False
        status_code=401
        def json(self):raise ValueError('private HTML')
    response=Response()
    monkeypatch.setattr(client.session,'request',lambda *a,**kw:response)
    with pytest.raises(ApiError,match='^HTTP_401$'):client.health()
    response.ok=True;response.status_code=200
    with pytest.raises(ApiError,match='^API_NON_JSON_RESPONSE$'):client.health()
