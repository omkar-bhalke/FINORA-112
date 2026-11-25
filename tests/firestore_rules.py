"""Run against the Firestore emulator started with firestore.rules and project demo-finora."""
import base64
import json
import os
import time
import unittest
import urllib.request
from urllib.error import HTTPError

HOST = os.getenv("FIRESTORE_EMULATOR_HOST", "127.0.0.1:8181")
assert HOST.startswith(("127.0.0.1:", "localhost:"))
ROOT = f"http://{HOST}/v1/projects/demo-finora/databases/(default)/documents"

def token(uid):
    now = int(time.time())
    def encode(obj):
        return base64.urlsafe_b64encode(json.dumps(obj).encode()).decode().rstrip("=")
    claims = {"sub": uid, "user_id": uid, "aud": "demo-finora",
              "iss": "https://securetoken.google.com/demo-finora", "iat": now,
              "exp": now + 3600, "auth_time": now, "firebase": {"sign_in_provider": "password"}}
    return encode({"alg":"none","typ":"JWT"}) + "." + encode(claims) + "."

def request(method, path, uid, fields=None):
    body = json.dumps({"fields": fields}).encode() if fields is not None else None
    req = urllib.request.Request(ROOT + path, data=body, method=method)
    req.add_header("Content-Type", "application/json")
    if uid:
        req.add_header("Authorization", "Bearer " + token(uid))
    try:
        with urllib.request.urlopen(req) as response:
            return response.status
    except HTTPError as err:
        return err.code

class RuleTests(unittest.TestCase):
    def test_profile_isolation(self):
        path = "/users/rules-owner"
        self.assertEqual(request("PATCH", path, "rules-owner", {"ready":{"booleanValue":True}}), 200)
        self.assertEqual(request("GET", path, "rules-owner"), 200)
        self.assertEqual(request("GET", path, "rules-other"), 403)
        self.assertEqual(request("GET", path, None), 403)

    def test_no_client_production_permit(self):
        self.assertEqual(request("PATCH", "/users/rules-owner/integrations/recipient",
                                 "rules-owner", {"productionApproved":{"booleanValue":True}}), 403)

    def test_no_negative_amount(self):
        self.assertEqual(request("PATCH", "/users/rules-owner", "rules-owner",
                                 {"daily":{"integerValue":"-1"}}), 403)

if __name__ == "__main__":
    unittest.main()
