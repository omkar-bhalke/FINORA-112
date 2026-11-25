"""Owner-only access tests for the local Realtime Database emulator."""
import base64
import json
import os
from pathlib import Path
import time
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

HOST = os.getenv("FIREBASE_DATABASE_EMULATOR_HOST", "127.0.0.1:9001")
assert HOST.startswith(("localhost:", "127.0.0.1:"))
def call(path, method="GET", token=None, body=None):
    url = f"http://{HOST}/{path}.json?ns=demo-finora"
    headers = {"Authorization": "Bearer owner"} if token == "owner" else {}
    if token and token != "owner":
        url += "&auth="+token
    req = Request(url, method=method,headers=headers,
                  data=None if body is None else json.dumps(body).encode())
    try:
        with urlopen(req) as res:
            return res.status
    except HTTPError as err:
        return err.code

def token(uid):
    now = int(time.time())
    def encode(obj):
        return base64.urlsafe_b64encode(json.dumps(obj).encode()).decode().rstrip("=")
    return encode({"alg":"none","typ":"JWT"})+"."+encode({"sub":uid,"user_id":uid,
        "aud":"demo-finora","iss":"https://securetoken.google.com/demo-finora","iat":now,
        "exp":now+3600,"auth_time":now,"firebase":{"sign_in_provider":"password"}})+"."

class LogRules(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        rules = json.loads((Path(__file__).resolve().parents[1]/"database.rules.json").read_text())
        assert call(".settings/rules","PUT","owner",rules)==200
    def test_owner_can_read_own_log(self):
        self.assertEqual(call("reportActivity/alice",token=token("alice")),200)
    def test_cross_account_denied(self):
        self.assertIn(call("reportActivity/alice",token=token("bob")),(401,403))
    def test_client_cannot_forge_log_or_change_backend(self):
        for path in ["reportActivity/alice/test","serviceConfig/reportApiUrl"]:
            self.assertIn(call(path,"PUT",token("alice"),"fake"),(401,403))
    def test_anonymous_denied(self):
        self.assertIn(call("reportActivity/alice"),(401,403))

if __name__=="__main__":
    unittest.main()
