/* eslint-disable require-jsdoc, max-len */
process.env.NODE_ENV = "test";

const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("fs");
const path = require("path");

const {
  __onboardingCompletionTest,
} = require("./index");

const SERVER_TIME = {sentinel: "server-time"};

class Snapshot {
  constructor(data) {
    this.value = data;
    this.exists = data !== undefined;
  }

  get(fieldPath) {
    return fieldPath.split(".").reduce((value, key) =>
      value && value[key], this.value);
  }
}

class FakeFirestore {
  constructor(seed = {}) {
    this.documents = new Map(Object.entries(seed).map(([key, value]) =>
      [key, clone(value)]));
    this.writes = [];
  }

  collection(name) {
    return {
      doc: (id) => ({
        path: `${name}/${id}`,
        get: async () => new Snapshot(clone(this.documents.get(`${name}/${id}`))),
        set: async (value, options) => {
          this.writes.push({path: `${name}/${id}`, value: clone(value), options});
          const current = options && options.merge ?
            clone(this.documents.get(`${name}/${id}`) || {}) : {};
          this.documents.set(`${name}/${id}`, merge(current, value));
        },
      }),
    };
  }
}

function clone(value) {
  if (value === undefined) return undefined;
  if (Array.isArray(value)) return value.map(clone);
  if (value && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([key, item]) =>
      [key, clone(item)]));
  }
  return value;
}

function merge(target, source) {
  Object.entries(source).forEach(([key, value]) => {
    if (value && typeof value === "object" &&
        !Array.isArray(value) && !value.sentinel) {
      target[key] = merge(clone(target[key] || {}), value);
    } else {
      target[key] = clone(value);
    }
  });
  return target;
}

function request(uid = "user-a", data = undefined) {
  return {
    auth: uid ? {uid} : undefined,
    data,
  };
}

async function assertRejectsWithCode(action, code) {
  await assert.rejects(action, (error) => {
    assert.equal(error.code, code);
    return true;
  });
}

test("getOnboardingCompletion rejects unauthenticated request", async () => {
  await assertRejectsWithCode(
      () => __onboardingCompletionTest.getOnboardingCompletionForRequest(
          request(null),
          new FakeFirestore(),
      ),
      "unauthenticated",
  );
});

test("markOnboardingCompleted rejects unauthenticated request", async () => {
  await assertRejectsWithCode(
      () => __onboardingCompletionTest.markOnboardingCompletedForRequest(
          request(null),
          new FakeFirestore(),
          {serverTimestamp: () => SERVER_TIME},
      ),
      "unauthenticated",
  );
});

test("onboarding callables reject arbitrary client payload", async () => {
  for (const payload of [
    {uid: "user-b"},
    {answers: {triggers: ["stress"]}},
    {onboardingCompleted: true},
  ]) {
    await assertRejectsWithCode(
        () => __onboardingCompletionTest.getOnboardingCompletionForRequest(
            request("user-a", payload),
            new FakeFirestore(),
        ),
        "invalid-argument",
    );
    await assertRejectsWithCode(
        () => __onboardingCompletionTest.markOnboardingCompletedForRequest(
            request("user-a", payload),
            new FakeFirestore(),
            {serverTimestamp: () => SERVER_TIME},
        ),
        "invalid-argument",
    );
  }
});

test("markOnboardingCompleted writes only account completion metadata", async () => {
  const db = new FakeFirestore();
  const result = await __onboardingCompletionTest.markOnboardingCompletedForRequest(
      request("user-a", {}),
      db,
      {serverTimestamp: () => SERVER_TIME},
  );

  assert.deepEqual(result, {success: true, onboardingCompleted: true});
  assert.deepEqual(db.documents.get("users/user-a"), {
    account: {
      onboardingCompleted: true,
      onboardingCompletedAt: SERVER_TIME,
    },
  });
});

test("markOnboardingCompleted is idempotent and preserves plus data", async () => {
  const db = new FakeFirestore({
    "users/user-a": {
      plus: {
        active: true,
        productId: "impulsive_plus_monthly",
      },
    },
  });

  await __onboardingCompletionTest.markOnboardingCompletedForRequest(
      request("user-a", {}),
      db,
      {serverTimestamp: () => SERVER_TIME},
  );
  await __onboardingCompletionTest.markOnboardingCompletedForRequest(
      request("user-a", {}),
      db,
      {serverTimestamp: () => SERVER_TIME},
  );

  assert.deepEqual(db.documents.get("users/user-a").plus, {
    active: true,
    productId: "impulsive_plus_monthly",
  });
  assert.equal(db.documents.get("users/user-a").account.onboardingCompleted, true);
  assert.equal(db.writes.length, 2);
});

test("user cannot specify or modify another uid", async () => {
  const db = new FakeFirestore({
    "users/user-b": {
      account: {onboardingCompleted: false},
    },
  });

  await assertRejectsWithCode(
      () => __onboardingCompletionTest.markOnboardingCompletedForRequest(
          request("user-a", {uid: "user-b"}),
          db,
          {serverTimestamp: () => SERVER_TIME},
      ),
      "invalid-argument",
  );
  assert.equal(db.documents.get("users/user-b").account.onboardingCompleted, false);
});

test("getOnboardingCompletion reads only authenticated user's marker", async () => {
  const db = new FakeFirestore({
    "users/user-a": {account: {onboardingCompleted: true}},
    "users/user-b": {account: {onboardingCompleted: false}},
  });

  assert.deepEqual(
      await __onboardingCompletionTest.getOnboardingCompletionForRequest(
          request("user-a", {}),
          db,
      ),
      {onboardingCompleted: true},
  );
  assert.deepEqual(
      await __onboardingCompletionTest.getOnboardingCompletionForRequest(
          request("user-b", {}),
          db,
      ),
      {onboardingCompleted: false},
  );
});

test("account deletion removes onboarding marker with users document", () => {
  const source = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
  assert.match(source, /async function eraseFirestoreDataForUser\(uid\)[\s\S]*await userRef\.delete\(\)/);
});

test("onboarding callables preserve App Check enforcement", () => {
  const source = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
  ["getOnboardingCompletion", "markOnboardingCompleted"].forEach((name) => {
    const start = source.indexOf(`exports.${name} = onCall(`);
    assert.notEqual(start, -1);
    const handler = source.slice(start, source.indexOf("async (request)", start));
    assert.match(handler, /enforceAppCheck:\s*true/);
  });
});

test("mark callable does not contain onboarding answer fields", () => {
  const source = fs.readFileSync(path.join(__dirname, "index.js"), "utf8");
  const start = source.indexOf("async function markOnboardingCompletedForRequest");
  const end = source.indexOf("function assertEmptyCallableData", start);
  const handler = source.slice(start, end);
  [
    "OnboardingAnswers",
    "interrupting",
    "timing",
    "triggers",
    "weekOneGoal",
    "dailyRelapseUrgeCount",
    "activeDayStartMinute",
    "activeDayEndMinute",
    "plannedReleaseWindowMinutes",
  ].forEach((field) => assert.equal(handler.includes(field), false, field));
});
