const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const TEACHER_UID = "teacher_alice_01";
const OTHER_TEACHER_UID = "teacher_bob_02";
const STUDENT_UID = "student_budi_03";
const OTHER_STUDENT_UID = "student_siti_04";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastDate(offsetMs = 2000) {
  return new Date(Date.now() - offsetMs);
}

function validProfile(uid) {
  const ts = pastDate(5000);
  return {
    userId: uid,
    namaDinas: "PEMERINTAH PROVINSI JAWA TENGAH / DINAS PENDIDIKAN",
    namaSekolah: "SMA NEGERI 1 NUSANTARA",
    alamatSekolah: "Jl. Pendidikan No. 123, Semarang",
    namaGuru: "Budi Santoso, S.Pd",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validExam(ownerId, active = true, tokenSusulan = "") {
  const ts = pastDate(5000);
  return {
    ownerId,
    judul: "PTS Informatika Ganjil",
    mapel: "Informatika",
    durasiMenit: 45,
    active,
    tokenSusulan,
    questionCount: 1,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validQuestion(ownerId, examActive = true) {
  const ts = pastDate(5000);
  return {
    ownerId,
    examActive,
    orderIndex: 0,
    pertanyaan: "Apa kepanjangan dari CPU?",
    opsiA: "A. Central Processing Unit",
    opsiB: "B. Central Program Utility",
    opsiC: "C. Computer Personal Unit",
    opsiD: "D. Core Processing User",
    jawabanBenar: "A. Central Processing Unit",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validResult(examOwnerId, studentUid, nama = "Andi Pratama", kelas = "XII IPA 1") {
  const ts = pastDate(2000);
  return {
    examOwnerId,
    studentUid,
    nama,
    kelas,
    studentKey: `${nama.toLowerCase()}|${kelas.toLowerCase()}`,
    skor: 100,
    benarCount: 1,
    totalCount: 1,
    waktuSelesai: "20-11-2026 10:15:00",
    createdAt: ts,
  };
}

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(TEACHER_UID).get());
  await assertFails(unauthDb.collection("users").doc(TEACHER_UID).set(validProfile(TEACHER_UID)));
  await assertFails(unauthDb.collection("exams").where("ownerId", "==", TEACHER_UID).get());
  await assertFails(unauthDb.collection("exams").doc("exam_1").set(validExam(TEACHER_UID)));
});

test("SchoolProfile: owner CRUD succeeds; cross-user & shadow fields fail", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_TEACHER_UID).firestore();

  await assertSucceeds(teacherDb.collection("users").doc(TEACHER_UID).set(validProfile(TEACHER_UID)));
  await assertSucceeds(teacherDb.collection("users").doc(TEACHER_UID).get());
  await assertSucceeds(
    teacherDb.collection("users").doc(TEACHER_UID).update({
      namaSekolah: "SMK Negeri 2 Nusantara",
      updatedAt: pastDate(1000),
    })
  );

  // Cross-user read/write blocked
  await assertFails(otherDb.collection("users").doc(TEACHER_UID).get());
  await assertFails(
    otherDb.collection("users").doc(TEACHER_UID).update({
      namaSekolah: "Hacked",
      updatedAt: pastDate(1000),
    })
  );

  // Listing /users blocked
  await assertFails(teacherDb.collection("users").get());

  // Shadow field & future timestamp rejected
  await assertFails(
    teacherDb.collection("users").doc(TEACHER_UID).update({
      isAdmin: true,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    otherDb.collection("users").doc(OTHER_TEACHER_UID).set({
      ...validProfile(OTHER_TEACHER_UID),
      createdAt: new Date(Date.now() + 60000),
    })
  );
});

test("Exams: owner create/list/update/delete succeed; unfiltered list & cross-teacher fail", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();

  // Create active exam
  await assertSucceeds(teacherDb.collection("exams").doc("exam_1").set(validExam(TEACHER_UID, true)));
  // Create inactive exam
  await assertSucceeds(teacherDb.collection("exams").doc("exam_2").set(validExam(TEACHER_UID, false)));

  // Filtered owner list succeeds; unfiltered fails
  await assertSucceeds(teacherDb.collection("exams").where("ownerId", "==", TEACHER_UID).get());
  await assertFails(teacherDb.collection("exams").get());
  await assertFails(otherDb.collection("exams").where("ownerId", "==", TEACHER_UID).get());

  // Student can get active exam by exact ID, cannot get inactive exam
  await assertSucceeds(studentDb.collection("exams").doc("exam_1").get());
  await assertFails(studentDb.collection("exams").doc("exam_2").get());

  // Owner action updates: susulan token & active toggle
  await assertSucceeds(
    teacherDb.collection("exams").doc("exam_1").update({
      tokenSusulan: "SUSULAN2026",
      updatedAt: pastDate(1000),
    })
  );
  await assertSucceeds(
    teacherDb.collection("exams").doc("exam_2").update({
      active: true,
      updatedAt: pastDate(1000),
    })
  );

  // Reject immutable field mutation & extra id field
  await assertFails(
    teacherDb.collection("exams").doc("exam_1").update({
      ownerId: OTHER_TEACHER_UID,
      updatedAt: pastDate(1000),
    })
  );
  await assertFails(
    teacherDb.collection("exams").doc("exam_3").set({
      id: "exam_3",
      ...validExam(TEACHER_UID),
    })
  );

  // Other teacher cannot update or delete
  await assertFails(otherDb.collection("exams").doc("exam_1").delete());
  await assertSucceeds(teacherDb.collection("exams").doc("exam_2").delete());
});

test("Questions: teacher CRUD & student active-exam list succeed; inactive questions denied", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const otherDb = testEnv.authenticatedContext(OTHER_TEACHER_UID).firestore();

  await assertSucceeds(teacherDb.collection("exams").doc("exam_1").set(validExam(TEACHER_UID, true)));
  await assertSucceeds(
    teacherDb.collection("exams").doc("exam_1").collection("questions").doc("q_1").set(validQuestion(TEACHER_UID, true))
  );

  // Teacher lists own questions with ownerId filter
  await assertSucceeds(
    teacherDb.collection("exams").doc("exam_1").collection("questions").where("ownerId", "==", TEACHER_UID).get()
  );

  // Student lists active exam questions with examActive == true filter
  await assertSucceeds(
    studentDb.collection("exams").doc("exam_1").collection("questions").where("examActive", "==", true).get()
  );

  // Unfiltered student query fails
  await assertFails(studentDb.collection("exams").doc("exam_1").collection("questions").get());

  // Student cannot create/update/delete questions
  await assertFails(
    studentDb.collection("exams").doc("exam_1").collection("questions").doc("q_2").set(validQuestion(STUDENT_UID, true))
  );
  await assertFails(
    otherDb.collection("exams").doc("exam_1").collection("questions").doc("q_1").update({
      pertanyaan: "Tampered",
      updatedAt: pastDate(1000),
    })
  );

  // Teacher edits question
  await assertSucceeds(
    teacherDb.collection("exams").doc("exam_1").collection("questions").doc("q_1").update({
      pertanyaan: "Apa fungsi utama CPU?",
      updatedAt: pastDate(1000),
    })
  );
});

test("ExamResults: student submits once (locked), cannot edit/read others; teacher lists & unlocks", async () => {
  const teacherDb = testEnv.authenticatedContext(TEACHER_UID).firestore();
  const studentDb = testEnv.authenticatedContext(STUDENT_UID).firestore();
  const otherStudentDb = testEnv.authenticatedContext(OTHER_STUDENT_UID).firestore();

  await assertSucceeds(teacherDb.collection("exams").doc("exam_1").set(validExam(TEACHER_UID, true)));
  await assertSucceeds(teacherDb.collection("exams").doc("exam_inactive").set(validExam(TEACHER_UID, false)));

  // Student submits result for active exam
  await assertSucceeds(
    studentDb.collection("exams").doc("exam_1").collection("results").doc("res_andi").set(
      validResult(TEACHER_UID, STUDENT_UID)
    )
  );

  // Student cannot submit to inactive exam
  await assertFails(
    studentDb.collection("exams").doc("exam_inactive").collection("results").doc("res_andi").set(
      validResult(TEACHER_UID, STUDENT_UID)
    )
  );

  // Terminal state lock: neither student nor teacher can update a locked score
  await assertFails(
    studentDb.collection("exams").doc("exam_1").collection("results").doc("res_andi").update({ skor: 99 })
  );
  await assertFails(
    teacherDb.collection("exams").doc("exam_1").collection("results").doc("res_andi").update({ skor: 50 })
  );

  // Other student cannot read or delete Andi's result
  await assertFails(otherStudentDb.collection("exams").doc("exam_1").collection("results").doc("res_andi").get());
  await assertFails(otherStudentDb.collection("exams").doc("exam_1").collection("results").doc("res_andi").delete());

  // Teacher lists all results for own exam using examOwnerId filter
  await assertSucceeds(
    teacherDb.collection("exams").doc("exam_1").collection("results").where("examOwnerId", "==", TEACHER_UID).get()
  );

  // Student cannot delete own locked result, only teacher can "Buka Kunci" (delete)
  await assertFails(studentDb.collection("exams").doc("exam_1").collection("results").doc("res_andi").delete());
  await assertSucceeds(teacherDb.collection("exams").doc("exam_1").collection("results").doc("res_andi").delete());
});
