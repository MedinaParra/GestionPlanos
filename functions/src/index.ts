import { initializeApp } from "firebase-admin/app";
import { getAuth } from "firebase-admin/auth";
import { FieldValue, getFirestore } from "firebase-admin/firestore";
import { HttpsError, onCall } from "firebase-functions/v2/https";

initializeApp();

const db = getFirestore();
const auth = getAuth();
const CORPORATE_DOMAIN = "skmindustrial.cl";
const VIEWER_DOMAIN = "viewer.skmindustrial.cl";

type Role = "ADMIN" | "EDITOR" | "VIEWER";

interface BootstrapPayload {
  displayName?: string;
}

interface CreateViewerPayload {
  username?: string;
  password?: string;
  displayName?: string;
}

export const bootstrapCorporateUser = onCall<BootstrapPayload>(async (request) => {
  const caller = request.auth;
  if (!caller) {
    throw new HttpsError("unauthenticated", "Debe iniciar sesión con Google.");
  }

  const uid = caller.uid;
  const email = String(caller.token.email ?? "").trim().toLowerCase();
  const emailVerified = caller.token.email_verified === true;
  const provider = String(caller.token.firebase?.sign_in_provider ?? "");

  if (!emailVerified || provider !== "google.com" || !email.endsWith(`@${CORPORATE_DOMAIN}`)) {
    throw new HttpsError(
      "permission-denied",
      `Solo se aceptan cuentas Google verificadas @${CORPORATE_DOMAIN}.`,
    );
  }

  const displayName = String(request.data?.displayName ?? caller.token.name ?? email.split("@")[0])
    .trim()
    .slice(0, 120);
  const bootstrapRef = db.collection("system").doc("bootstrap");
  const userRef = db.collection("users").doc(uid);
  let role: Role = "EDITOR";

  await db.runTransaction(async (transaction) => {
    const [bootstrapSnapshot, userSnapshot] = await Promise.all([
      transaction.get(bootstrapRef),
      transaction.get(userRef),
    ]);

    const existingRole = userSnapshot.get("role") as Role | undefined;
    if (existingRole === "ADMIN" || existingRole === "EDITOR") {
      role = existingRole;
    } else if (!bootstrapSnapshot.exists) {
      role = "ADMIN";
      transaction.create(bootstrapRef, {
        ownerUid: uid,
        ownerEmail: email,
        createdAt: FieldValue.serverTimestamp(),
      });
    } else {
      role = "EDITOR";
    }

    transaction.set(
      userRef,
      {
        email,
        displayName,
        role,
        accountType: "CORPORATE_GOOGLE",
        active: true,
        updatedAt: FieldValue.serverTimestamp(),
        createdAt: userSnapshot.exists
          ? userSnapshot.get("createdAt") ?? FieldValue.serverTimestamp()
          : FieldValue.serverTimestamp(),
      },
      { merge: true },
    );
  });

  const authUser = await auth.getUser(uid);
  await auth.setCustomUserClaims(uid, {
    ...(authUser.customClaims ?? {}),
    role,
    companyDomain: CORPORATE_DOMAIN,
  });

  return { role };
});

export const createViewerUser = onCall<CreateViewerPayload>(async (request) => {
  const caller = request.auth;
  if (!caller) {
    throw new HttpsError("unauthenticated", "Debe iniciar sesión.");
  }
  if (caller.token.role !== "ADMIN") {
    throw new HttpsError("permission-denied", "Solo el administrador puede crear visualizadores.");
  }

  const username = normalizeUsername(request.data?.username);
  const password = String(request.data?.password ?? "");
  const displayName = String(request.data?.displayName ?? username).trim().slice(0, 120);

  if (password.length < 8 || password.length > 128) {
    throw new HttpsError(
      "invalid-argument",
      "La contraseña debe tener entre 8 y 128 caracteres.",
    );
  }

  const email = `${username}@${VIEWER_DOMAIN}`;
  let userRecord;
  try {
    userRecord = await auth.createUser({
      email,
      password,
      displayName: displayName || username,
      emailVerified: true,
      disabled: false,
    });
  } catch (error: unknown) {
    const code = getErrorCode(error);
    if (code === "auth/email-already-exists") {
      throw new HttpsError("already-exists", "Ese nombre de usuario ya existe.");
    }
    throw new HttpsError("internal", "No fue posible crear el usuario visualizador.");
  }

  await auth.setCustomUserClaims(userRecord.uid, {
    role: "VIEWER",
    accountType: "PASSWORD_VIEWER",
  });

  await db.collection("users").doc(userRecord.uid).set({
    username,
    email,
    displayName: displayName || username,
    role: "VIEWER",
    accountType: "PASSWORD_VIEWER",
    active: true,
    createdByUid: caller.uid,
    createdAt: FieldValue.serverTimestamp(),
    updatedAt: FieldValue.serverTimestamp(),
  });

  return {
    uid: userRecord.uid,
    username,
  };
});

function normalizeUsername(value: unknown): string {
  const username = String(value ?? "").trim().toLowerCase();
  if (!/^[a-z0-9._-]{3,32}$/.test(username)) {
    throw new HttpsError(
      "invalid-argument",
      "El usuario debe tener 3 a 32 caracteres: letras, números, punto, guion o guion bajo.",
    );
  }
  return username;
}

function getErrorCode(error: unknown): string {
  if (typeof error !== "object" || error === null || !("code" in error)) {
    return "";
  }
  return String((error as { code?: unknown }).code ?? "");
}
