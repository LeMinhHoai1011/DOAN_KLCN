import json
import mimetypes
import os
import requests


class ApiError(Exception):
    def __init__(self, code):
        super().__init__(code)
        self.code = code


class ApiClient:
    def __init__(self, config):
        self.url = config['api_url'].rstrip('/')
        self.timeout = config.get('timeout_seconds', 900)
        token = os.environ.get(config.get('token_env', 'BENCHMARK_JWT'))
        if not token:
            raise ApiError('JWT_MISSING')
        token=token.strip()
        if token.startswith('Bearer '):
            token=token[len('Bearer '):]
        if not token or not token.isascii() or any(c.isspace() or c in '<>\"\'' for c in token):
            raise ApiError('JWT_INVALID_VALUE')
        self.session = requests.Session()
        self.session.headers['Authorization'] = 'Bearer ' + token

    def request(self, method, path, **kwargs):
        try:
            response = self.session.request(method, self.url + path, timeout=self.timeout, allow_redirects=False, **kwargs)
            if not response.ok:
                raise ApiError('HTTP_' + str(response.status_code))
            try:
                return response.json()
            except ValueError:
                raise ApiError('API_NON_JSON_RESPONSE') from None
        except requests.Timeout:
            raise ApiError('API_TIMEOUT') from None
        except requests.ConnectionError:
            raise ApiError('API_UNAVAILABLE') from None
        except (ValueError, requests.RequestException):
            raise ApiError('API_INVALID_RESPONSE') from None

    def health(self):
        return self.request('GET', '/api/benchmark/health')

    def identity(self):
        user = self.request('GET', '/api/v1/users/me')
        roles = set(user.get('roles', [])) | {user.get('role')}
        if not roles.intersection({'ADMIN', 'ACCOUNTANT', 'ROLE_ADMIN', 'ROLE_ACCOUNTANT'}):
            raise ApiError('CONFIRMER_ROLE_REQUIRED')
        if not user.get('id') or not user.get('username'):
            raise ApiError('INVALID_CONFIRMER_IDENTITY')
        return {'id': user['id'], 'username': user['username']}

    def process(self, path, stage, options):
        with path.open('rb') as f:
            result = self.request('POST', '/api/benchmark/' + stage,
                                  files={'file': ('benchmark' + path.suffix, f, mimetypes.guess_type(path.name)[0])},
                                  data={'mode': 'SYSTEM', 'options': json.dumps(options)})
        if not isinstance(result, dict) or result.get('status') not in {'SUCCESS', 'FAILED'}:
            raise ApiError('API_SCHEMA_ERROR')
        normalized = result.get('normalized')
        if result['status'] == 'SUCCESS' and (not isinstance(result.get('ocr'), dict)
                or (stage == 'ai' and (not isinstance(normalized, dict) or not isinstance(normalized.get('result'), dict)))):
            raise ApiError('API_SCHEMA_ERROR')
        return result
