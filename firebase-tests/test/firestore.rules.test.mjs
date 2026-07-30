import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, beforeEach, describe, it } from "node:test";

import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  collection,
  collectionGroup,
  deleteDoc,
  doc,
  getDoc,
  getDocs,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  Timestamp,
  updateDoc,
  where,
  writeBatch,
} from "firebase/firestore";

const PROJECT_ID = "demo-meds-widget-rules";
const ALICE = "alice-user";
const BOB = "bob-user";
const LOGICAL_DAY = "2026-07-29";
const MEDICINE_ID = "medicineA";
const STATE_ID = `${LOGICAL_DAY}_${MEDICINE_ID}_afternoon`;
const CHECK_EVENT_ID = "checkEvent0000000001";
const UNDO_EVENT_ID = "undoEvent00000000001";
const RECHECK_EVENT_ID = "recheckEvent00000001";
const CHECKED_AT = Timestamp.fromDate(new Date("2026-07-29T05:14:00.000Z"));
const UNDONE_AT = Timestamp.fromDate(new Date("2026-07-29T05:19:00.000Z"));
const RECHECKED_AT = Timestamp.fromDate(
  new Date("2026-07-29T05:24:00.000Z"),
);
const FIXED_SERVER_TIME = Timestamp.fromDate(
  new Date("2026-07-29T05:30:00.000Z"),
);

let testEnv;

function authenticatedDb(uid) {
  return testEnv.authenticatedContext(uid).firestore();
}

function anonymousDb() {
  return testEnv.unauthenticatedContext().firestore();
}

function userDocument(uid, overrides = {}, useServerTimestamp = true) {
  const timestamp = useServerTimestamp ? serverTimestamp() : FIXED_SERVER_TIME;
  return {
    ownerUid: uid,
    createdAt: timestamp,
    updatedAt: timestamp,
    schemaVersion: 1,
    ...overrides,
  };
}

function settingsDocument(uid, overrides = {}, useServerTimestamp = true) {
  return {
    ownerUid: uid,
    resetMinutesAfterMidnight: 0,
    timezoneId: "Asia/Singapore",
    displayName: "Alice",
    themePreference: "system",
    updatedAt: useServerTimestamp ? serverTimestamp() : FIXED_SERVER_TIME,
    schemaVersion: 1,
    ...overrides,
  };
}

function medicineDocument(
  uid,
  medicineId = MEDICINE_ID,
  overrides = {},
  useServerTimestamp = true,
) {
  const timestamp = useServerTimestamp ? serverTimestamp() : FIXED_SERVER_TIME;
  return {
    id: medicineId,
    ownerUid: uid,
    name: "Medicine A",
    afternoonEnabled: true,
    afternoonLabel: "After lunch",
    nightEnabled: true,
    nightLabel: "Before bed",
    archived: false,
    createdAt: timestamp,
    updatedAt: timestamp,
    schemaVersion: 1,
    ...overrides,
  };
}

function doseStateDocument(
  uid,
  eventId = CHECK_EVENT_ID,
  overrides = {},
  useServerTimestamp = true,
) {
  return {
    ownerUid: uid,
    logicalDay: LOGICAL_DAY,
    medicineId: MEDICINE_ID,
    slot: "afternoon",
    labelSnapshot: "After lunch",
    medicineNameSnapshot: "Medicine A",
    isTaken: true,
    checkedAt: CHECKED_AT,
    checkedTimezone: "Asia/Singapore",
    checkedSource: "app",
    undoneAt: null,
    lastActionId: eventId,
    updatedAt: useServerTimestamp ? serverTimestamp() : FIXED_SERVER_TIME,
    schemaVersion: 1,
    ...overrides,
  };
}

function doseEventDocument(
  uid,
  eventId = CHECK_EVENT_ID,
  overrides = {},
  useServerTimestamp = true,
) {
  return {
    eventId,
    ownerUid: uid,
    action: "check",
    logicalDay: LOGICAL_DAY,
    medicineId: MEDICINE_ID,
    medicineNameSnapshot: "Medicine A",
    slot: "afternoon",
    labelSnapshot: "After lunch",
    occurredAt: CHECKED_AT,
    timezoneId: "Asia/Singapore",
    source: "app",
    relatedStateId: STATE_ID,
    previousActionId: null,
    syncedAt: useServerTimestamp ? serverTimestamp() : FIXED_SERVER_TIME,
    schemaVersion: 1,
    ...overrides,
  };
}

async function seedDocument(path, data) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await setDoc(doc(context.firestore(), path), data);
  });
}

async function commitCheck(
  db,
  {
    uid = ALICE,
    eventId = CHECK_EVENT_ID,
    stateId = STATE_ID,
    stateOverrides = {},
    eventOverrides = {},
  } = {},
) {
  const batch = writeBatch(db);
  batch.set(
    doc(db, `users/${uid}/doseStates/${stateId}`),
    doseStateDocument(uid, eventId, stateOverrides),
  );
  batch.set(
    doc(db, `users/${uid}/doseEvents/${eventId}`),
    doseEventDocument(uid, eventId, eventOverrides),
  );
  return batch.commit();
}

async function commitUndo(
  db,
  {
    uid = ALICE,
    eventId = UNDO_EVENT_ID,
    source = "app",
    eventOverrides = {},
  } = {},
) {
  const batch = writeBatch(db);
  batch.update(doc(db, `users/${uid}/doseStates/${STATE_ID}`), {
    isTaken: false,
    undoneAt: UNDONE_AT,
    lastActionId: eventId,
    updatedAt: serverTimestamp(),
  });
  batch.set(
    doc(db, `users/${uid}/doseEvents/${eventId}`),
    doseEventDocument(uid, eventId, {
      action: "undo",
      occurredAt: UNDONE_AT,
      source,
      previousActionId: CHECK_EVENT_ID,
      ...eventOverrides,
    }),
  );
  return batch.commit();
}

before(async () => {
  const rules = await readFile(
    new URL("../../firestore.rules", import.meta.url),
    "utf8",
  );
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: "127.0.0.1",
      port: 8080,
      rules,
    },
  });
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

describe("authentication and user path isolation", () => {
  it("denies anonymous reads", async () => {
    await seedDocument(
      `users/${ALICE}/medicines/${MEDICINE_ID}`,
      medicineDocument(ALICE, MEDICINE_ID, {}, false),
    );

    await assertFails(
      getDoc(
        doc(
          anonymousDb(),
          `users/${ALICE}/medicines/${MEDICINE_ID}`,
        ),
      ),
    );
  });

  it("denies anonymous writes", async () => {
    const db = anonymousDb();
    await assertFails(
      setDoc(
        doc(db, `users/${ALICE}/medicines/${MEDICINE_ID}`),
        medicineDocument(ALICE),
      ),
    );
  });

  it("allows an owner to create, get, and list valid medicines", async () => {
    const db = authenticatedDb(ALICE);
    const medicineRef = doc(
      db,
      `users/${ALICE}/medicines/${MEDICINE_ID}`,
    );

    await assertSucceeds(setDoc(medicineRef, medicineDocument(ALICE)));
    const snapshot = await assertSucceeds(getDoc(medicineRef));
    assert.equal(snapshot.data().name, "Medicine A");

    const list = await assertSucceeds(
      getDocs(collection(db, `users/${ALICE}/medicines`)),
    );
    assert.equal(list.size, 1);
  });

  it("allows the indexed active-medicine query within the owner's path", async () => {
    const db = authenticatedDb(ALICE);
    await setDoc(
      doc(db, `users/${ALICE}/medicines/medicineA`),
      medicineDocument(ALICE, "medicineA"),
    );
    await setDoc(
      doc(db, `users/${ALICE}/medicines/medicineB`),
      medicineDocument(ALICE, "medicineB", {
        name: "Medicine B",
        archived: true,
      }),
    );

    const activeMedicines = await assertSucceeds(
      getDocs(
        query(
          collection(db, `users/${ALICE}/medicines`),
          where("archived", "==", false),
          orderBy("createdAt", "asc"),
        ),
      ),
    );
    assert.deepEqual(
      activeMedicines.docs.map((snapshot) => snapshot.id),
      ["medicineA"],
    );
  });

  it("denies cross-user reads and collection lists", async () => {
    await seedDocument(
      `users/${ALICE}/medicines/${MEDICINE_ID}`,
      medicineDocument(ALICE, MEDICINE_ID, {}, false),
    );
    const bobDb = authenticatedDb(BOB);

    await assertFails(
      getDoc(
        doc(
          bobDb,
          `users/${ALICE}/medicines/${MEDICINE_ID}`,
        ),
      ),
    );
    await assertFails(
      getDocs(collection(bobDb, `users/${ALICE}/medicines`)),
    );
  });

  it("denies cross-user writes even when the payload owner is forged", async () => {
    const aliceDb = authenticatedDb(ALICE);

    await assertFails(
      setDoc(
        doc(
          aliceDb,
          `users/${BOB}/medicines/${MEDICINE_ID}`,
        ),
        medicineDocument(BOB),
      ),
    );
  });

  it("denies a wrong ownerUid inside the caller's path", async () => {
    const db = authenticatedDb(ALICE);

    await assertFails(
      setDoc(
        doc(db, `users/${ALICE}/medicines/${MEDICINE_ID}`),
        medicineDocument(BOB),
      ),
    );
  });

  it("does not allow users collection or collection-group discovery", async () => {
    await seedDocument(
      `users/${ALICE}/medicines/${MEDICINE_ID}`,
      medicineDocument(ALICE, MEDICINE_ID, {}, false),
    );
    const db = authenticatedDb(ALICE);

    await assertFails(getDocs(collection(db, "users")));
    await assertFails(getDocs(collectionGroup(db, "medicines")));
  });
});

describe("strict user, settings, and medicine schemas", () => {
  it("allows an owner to create and update the strict root user document", async () => {
    const db = authenticatedDb(ALICE);
    const userRef = doc(db, `users/${ALICE}`);

    await assertSucceeds(setDoc(userRef, userDocument(ALICE)));
    const created = await getDoc(userRef);
    await assertSucceeds(
      setDoc(
        userRef,
        userDocument(ALICE, {
          createdAt: created.data().createdAt,
        }),
      ),
    );
  });

  it("allows only the preferences settings document and validates its values", async () => {
    const db = authenticatedDb(ALICE);

    await assertSucceeds(
      setDoc(
        doc(db, `users/${ALICE}/settings/preferences`),
        settingsDocument(ALICE),
      ),
    );
    await assertFails(
      setDoc(
        doc(db, `users/${ALICE}/settings/another`),
        settingsDocument(ALICE),
      ),
    );
    await assertFails(
      updateDoc(
        doc(db, `users/${ALICE}/settings/preferences`),
        {
          resetMinutesAfterMidnight: 1440,
          updatedAt: serverTimestamp(),
        },
      ),
    );
  });

  it("rejects invalid schemas and unknown medicine fields", async () => {
    const db = authenticatedDb(ALICE);

    await assertFails(
      setDoc(
        doc(db, `users/${ALICE}/medicines/${MEDICINE_ID}`),
        medicineDocument(ALICE, MEDICINE_ID, {
          schemaVersion: 2,
        }),
      ),
    );
    await assertFails(
      setDoc(
        doc(db, `users/${ALICE}/medicines/${MEDICINE_ID}`),
        medicineDocument(ALICE, MEDICINE_ID, {
          unrecognisedField: "must be rejected",
        }),
      ),
    );
  });

  it("enforces medicine name, label, id, and enabled-slot constraints", async () => {
    const db = authenticatedDb(ALICE);
    const medicineRef = doc(
      db,
      `users/${ALICE}/medicines/${MEDICINE_ID}`,
    );

    await assertFails(
      setDoc(
        medicineRef,
        medicineDocument(ALICE, MEDICINE_ID, { name: "" }),
      ),
    );
    await assertFails(
      setDoc(
        medicineRef,
        medicineDocument(ALICE, MEDICINE_ID, {
          name: "m".repeat(101),
        }),
      ),
    );
    await assertFails(
      setDoc(
        medicineRef,
        medicineDocument(ALICE, MEDICINE_ID, {
          afternoonLabel: "a".repeat(61),
        }),
      ),
    );
    await assertFails(
      setDoc(
        medicineRef,
        medicineDocument(ALICE, MEDICINE_ID, {
          afternoonEnabled: false,
          nightEnabled: false,
        }),
      ),
    );
    await assertFails(
      setDoc(
        medicineRef,
        medicineDocument(ALICE, "differentId"),
      ),
    );
  });

  it("allows medicine updates but prevents identity and creation-time changes", async () => {
    const db = authenticatedDb(ALICE);
    const medicineRef = doc(
      db,
      `users/${ALICE}/medicines/${MEDICINE_ID}`,
    );
    await setDoc(medicineRef, medicineDocument(ALICE));
    const created = (await getDoc(medicineRef)).data();

    await assertSucceeds(
      updateDoc(medicineRef, {
        name: "Medicine A renamed",
        updatedAt: serverTimestamp(),
      }),
    );
    await assertFails(
      updateDoc(medicineRef, {
        createdAt: serverTimestamp(),
        updatedAt: serverTimestamp(),
      }),
    );
    await assertFails(
      updateDoc(medicineRef, {
        ownerUid: BOB,
        updatedAt: serverTimestamp(),
      }),
    );

    assert.ok(created.createdAt instanceof Timestamp);
  });
});

describe("atomic dose state and immutable event audit", () => {
  it("allows an owner check batch with a deterministic state id", async () => {
    const db = authenticatedDb(ALICE);

    await assertSucceeds(commitCheck(db));

    const state = await getDoc(
      doc(db, `users/${ALICE}/doseStates/${STATE_ID}`),
    );
    const event = await getDoc(
      doc(db, `users/${ALICE}/doseEvents/${CHECK_EVENT_ID}`),
    );
    assert.equal(state.data().isTaken, true);
    assert.equal(event.data().action, "check");
    assert.ok(event.data().syncedAt instanceof Timestamp);
  });

  it("allows all supported check sources", async () => {
    for (const [index, source] of [
      "app",
      "app_preview",
      "widget_2x2",
      "widget_4x2",
    ].entries()) {
      await testEnv.clearFirestore();
      const db = authenticatedDb(ALICE);
      const eventId = `supportedSourceEvent0${index}`;
      await assertSucceeds(
        commitCheck(db, {
          eventId,
          stateOverrides: { checkedSource: source },
          eventOverrides: { source },
        }),
      );
    }
  });

  it("rejects state or event writes that are not atomically paired", async () => {
    const db = authenticatedDb(ALICE);

    await assertFails(
      setDoc(
        doc(db, `users/${ALICE}/doseStates/${STATE_ID}`),
        doseStateDocument(ALICE),
      ),
    );
    await assertFails(
      setDoc(
        doc(db, `users/${ALICE}/doseEvents/${CHECK_EVENT_ID}`),
        doseEventDocument(ALICE),
      ),
    );
  });

  it("rejects mismatched state ids, malformed days, and unsupported sources", async () => {
    const db = authenticatedDb(ALICE);

    await assertFails(
      commitCheck(db, {
        stateId: "wrong-state-id",
        eventOverrides: { relatedStateId: "wrong-state-id" },
      }),
    );
    await assertFails(
      commitCheck(db, {
        stateId: "2026-7-29_medicineA_afternoon",
        stateOverrides: { logicalDay: "2026-7-29" },
        eventOverrides: {
          logicalDay: "2026-7-29",
          relatedStateId: "2026-7-29_medicineA_afternoon",
        },
      }),
    );
    await assertFails(
      commitCheck(db, {
        stateOverrides: { checkedSource: "unknown" },
        eventOverrides: { source: "unknown" },
      }),
    );
    await assertFails(
      commitCheck(db, {
        eventOverrides: { previousActionId: "forgedPreviousAction01" },
      }),
    );
  });

  it("rejects unsupported slots and oversized snapshot payloads", async () => {
    const db = authenticatedDb(ALICE);

    await assertFails(
      commitCheck(db, {
        stateId: `${LOGICAL_DAY}_${MEDICINE_ID}_morning`,
        stateOverrides: { slot: "morning" },
        eventOverrides: {
          slot: "morning",
          relatedStateId: `${LOGICAL_DAY}_${MEDICINE_ID}_morning`,
        },
      }),
    );
    await assertFails(
      commitCheck(db, {
        stateOverrides: { labelSnapshot: "x".repeat(61) },
        eventOverrides: { labelSnapshot: "x".repeat(61) },
      }),
    );
    await assertFails(
      commitCheck(db, {
        stateOverrides: { medicineNameSnapshot: "x".repeat(101) },
        eventOverrides: { medicineNameSnapshot: "x".repeat(101) },
      }),
    );
  });

  it("rejects wrong ownerUid values in dose state and event documents", async () => {
    const db = authenticatedDb(ALICE);

    await assertFails(
      commitCheck(db, {
        stateOverrides: { ownerUid: BOB },
        eventOverrides: { ownerUid: BOB },
      }),
    );
  });

  it("allows app undo while retaining the prior check and immutable events", async () => {
    const db = authenticatedDb(ALICE);
    await commitCheck(db);

    await assertSucceeds(commitUndo(db));

    const state = await getDoc(
      doc(db, `users/${ALICE}/doseStates/${STATE_ID}`),
    );
    const originalEvent = await getDoc(
      doc(db, `users/${ALICE}/doseEvents/${CHECK_EVENT_ID}`),
    );
    const undoEvent = await getDoc(
      doc(db, `users/${ALICE}/doseEvents/${UNDO_EVENT_ID}`),
    );
    assert.equal(state.data().isTaken, false);
    assert.equal(
      state.data().checkedAt.toMillis(),
      CHECKED_AT.toMillis(),
    );
    assert.equal(originalEvent.data().action, "check");
    assert.equal(undoEvent.data().action, "undo");
    assert.equal(undoEvent.data().previousActionId, CHECK_EVENT_ID);
    assert.equal(
      undoEvent.data().occurredAt.toMillis(),
      UNDONE_AT.toMillis(),
    );
  });

  it("denies widget and app-preview undo attempts", async () => {
    for (const source of [
      "app_preview",
      "widget_2x2",
      "widget_4x2",
    ]) {
      await testEnv.clearFirestore();
      const db = authenticatedDb(ALICE);
      await commitCheck(db);

      await assertFails(commitUndo(db, { source }));
      const state = await getDoc(
        doc(db, `users/${ALICE}/doseStates/${STATE_ID}`),
      );
      assert.equal(state.data().isTaken, true);
    }
  });

  it("prevents a second active check and duplicate audit event", async () => {
    const db = authenticatedDb(ALICE);
    await commitCheck(db);

    await assertFails(
      commitCheck(db, {
        eventId: "secondCheckEvent0001",
        stateOverrides: {
          checkedAt: RECHECKED_AT,
          lastActionId: "secondCheckEvent0001",
        },
        eventOverrides: {
          occurredAt: RECHECKED_AT,
        },
      }),
    );

    const events = await getDocs(
      collection(db, `users/${ALICE}/doseEvents`),
    );
    assert.equal(events.size, 1);
  });

  it("allows a fresh check after an app undo", async () => {
    const db = authenticatedDb(ALICE);
    await commitCheck(db);
    await commitUndo(db);

    const batch = writeBatch(db);
    batch.update(doc(db, `users/${ALICE}/doseStates/${STATE_ID}`), {
      isTaken: true,
      checkedAt: RECHECKED_AT,
      checkedTimezone: "Asia/Singapore",
      checkedSource: "widget_4x2",
      undoneAt: null,
      lastActionId: RECHECK_EVENT_ID,
      updatedAt: serverTimestamp(),
    });
    batch.set(
      doc(db, `users/${ALICE}/doseEvents/${RECHECK_EVENT_ID}`),
      doseEventDocument(ALICE, RECHECK_EVENT_ID, {
        occurredAt: RECHECKED_AT,
        source: "widget_4x2",
      }),
    );

    await assertSucceeds(batch.commit());
    const state = await getDoc(
      doc(db, `users/${ALICE}/doseStates/${STATE_ID}`),
    );
    assert.equal(state.data().isTaken, true);
    assert.equal(
      state.data().checkedAt.toMillis(),
      RECHECKED_AT.toMillis(),
    );
  });

  it("denies every audit event update after creation", async () => {
    const db = authenticatedDb(ALICE);
    await commitCheck(db);
    const eventRef = doc(
      db,
      `users/${ALICE}/doseEvents/${CHECK_EVENT_ID}`,
    );

    await assertFails(
      updateDoc(eventRef, {
        labelSnapshot: "Altered history",
      }),
    );
    await assertFails(
      setDoc(eventRef, doseEventDocument(ALICE, CHECK_EVENT_ID)),
    );
  });

  it("allows owner list queries for current state and history", async () => {
    const db = authenticatedDb(ALICE);
    await commitCheck(db);

    const states = await assertSucceeds(
      getDocs(collection(db, `users/${ALICE}/doseStates`)),
    );
    const events = await assertSucceeds(
      getDocs(
        query(
          collection(db, `users/${ALICE}/doseEvents`),
          orderBy("logicalDay", "desc"),
          orderBy("occurredAt", "desc"),
        ),
      ),
    );
    assert.equal(states.size, 1);
    assert.equal(events.size, 1);
  });
});

describe("client-side account data deletion", () => {
  it("allows only the path owner to delete every supported account document", async () => {
    const pathsAndData = [
      [`users/${ALICE}`, userDocument(ALICE, {}, false)],
      [
        `users/${ALICE}/settings/preferences`,
        settingsDocument(ALICE, {}, false),
      ],
      [
        `users/${ALICE}/medicines/${MEDICINE_ID}`,
        medicineDocument(ALICE, MEDICINE_ID, {}, false),
      ],
      [
        `users/${ALICE}/doseStates/${STATE_ID}`,
        doseStateDocument(ALICE, CHECK_EVENT_ID, {}, false),
      ],
      [
        `users/${ALICE}/doseEvents/${CHECK_EVENT_ID}`,
        doseEventDocument(ALICE, CHECK_EVENT_ID, {}, false),
      ],
    ];

    for (const [path, data] of pathsAndData) {
      await seedDocument(path, data);
    }

    const bobDb = authenticatedDb(BOB);
    for (const [path] of pathsAndData) {
      await assertFails(deleteDoc(doc(bobDb, path)));
    }

    const aliceDb = authenticatedDb(ALICE);
    // Firestore clients delete subcollections explicitly before the root
    // document; deleting a parent never recursively deletes its children.
    for (const [path] of pathsAndData.slice(1).reverse()) {
      await assertSucceeds(deleteDoc(doc(aliceDb, path)));
    }
    await assertSucceeds(deleteDoc(doc(aliceDb, `users/${ALICE}`)));
  });

  it("denies access to unknown nested collections", async () => {
    await seedDocument(`users/${ALICE}/unknown/private`, {
      ownerUid: ALICE,
      value: "private",
    });
    const db = authenticatedDb(ALICE);

    await assertFails(
      getDoc(doc(db, `users/${ALICE}/unknown/private`)),
    );
    await assertFails(
      deleteDoc(doc(db, `users/${ALICE}/unknown/private`)),
    );
  });
});
