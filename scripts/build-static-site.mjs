import { cpSync, rmSync } from "node:fs";
import { resolve } from "node:path";

const root = process.cwd();
const publicDir = resolve(root, "public");
const distDir = resolve(root, "dist");

rmSync(distDir, { recursive: true, force: true });
cpSync(publicDir, distDir, { recursive: true });
