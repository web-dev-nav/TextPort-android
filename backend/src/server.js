import express from 'express';
import cors from 'cors';
import helmet from 'helmet';
import rateLimit from 'express-rate-limit';
import morgan from 'morgan';
import bcrypt from 'bcryptjs';
import { z } from 'zod';
import db from './db.js';
import { requireAuth, signToken } from './auth.js';

const app = express();
app.use(helmet());
app.use(cors({ origin: process.env.CORS_ORIGIN?.split(',') || '*' }));
app.use(express.json({ limit: '1mb' }));
app.use(morgan('combined'));
app.use(rateLimit({ windowMs: 60_000, max: 120 }));

const authSchema = z.object({ email: z.string().email(), password: z.string().min(8) });
const syncSchema = z.object({
  messages: z.array(z.object({
    sender: z.string().min(1),
    body: z.string(),
    timestamp: z.number().int(),
    direction: z.string().default('inbound')
  })).min(1).max(100)
});

app.get('/api/health', (_, res) => res.json({ ok: true }));

app.post('/api/auth/register', async (req, res) => {
  const parsed = authSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });
  const { email, password } = parsed.data;

  const existing = db.prepare('SELECT id FROM users WHERE email = ?').get(email);
  if (existing) return res.status(409).json({ error: 'Email already exists' });

  const passwordHash = await bcrypt.hash(password, 12);
  const result = db.prepare('INSERT INTO users (email, password_hash) VALUES (?, ?)').run(email, passwordHash);
  const user = { id: result.lastInsertRowid, email };
  return res.status(201).json({ token: signToken(user) });
});

app.post('/api/auth/login', async (req, res) => {
  const parsed = authSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid input' });

  const user = db.prepare('SELECT * FROM users WHERE email = ?').get(parsed.data.email);
  if (!user) return res.status(401).json({ error: 'Invalid credentials' });

  const ok = await bcrypt.compare(parsed.data.password, user.password_hash);
  if (!ok) return res.status(401).json({ error: 'Invalid credentials' });

  return res.json({ token: signToken(user) });
});

app.post('/api/messages/sync', requireAuth, (req, res) => {
  const parsed = syncSchema.safeParse(req.body);
  if (!parsed.success) return res.status(400).json({ error: 'Invalid payload' });

  const user = db.prepare('SELECT sync_enabled FROM users WHERE id = ?').get(req.user.sub);
  if (!user) return res.status(404).json({ error: 'User not found' });
  if (!user.sync_enabled) return res.status(403).json({ error: 'Sync paused' });

  const stmt = db.prepare('INSERT INTO messages (user_id, sender, body, timestamp, direction) VALUES (?, ?, ?, ?, ?)');
  const insertMany = db.transaction((messages) => {
    for (const m of messages) stmt.run(req.user.sub, m.sender, m.body, m.timestamp, m.direction);
  });
  insertMany(parsed.data.messages);

  res.json({ ok: true });
});

app.get('/api/messages', requireAuth, (req, res) => {
  const q = String(req.query.q || '').trim();
  const sender = String(req.query.sender || '').trim();
  const limit = Math.min(Number(req.query.limit || 100), 500);

  const rows = db.prepare(`
    SELECT id, sender, body, timestamp, direction
    FROM messages
    WHERE user_id = @uid
      AND (@q = '' OR body LIKE '%' || @q || '%')
      AND (@sender = '' OR sender = @sender)
    ORDER BY timestamp DESC
    LIMIT @limit
  `).all({ uid: req.user.sub, q, sender, limit });

  res.json({ items: rows });
});

app.post('/api/account/pause', requireAuth, (req, res) => {
  db.prepare('UPDATE users SET sync_enabled = 0 WHERE id = ?').run(req.user.sub);
  res.json({ ok: true });
});

app.post('/api/account/resume', requireAuth, (req, res) => {
  db.prepare('UPDATE users SET sync_enabled = 1 WHERE id = ?').run(req.user.sub);
  res.json({ ok: true });
});

app.get('/api/account/export', requireAuth, (req, res) => {
  const rows = db.prepare('SELECT sender, body, timestamp, direction FROM messages WHERE user_id = ? ORDER BY timestamp DESC').all(req.user.sub);
  res.json({ download_url: `data:application/json,${encodeURIComponent(JSON.stringify(rows))}` });
});

app.post('/api/account/delete', requireAuth, (req, res) => {
  db.prepare('DELETE FROM messages WHERE user_id = ?').run(req.user.sub);
  db.prepare('DELETE FROM users WHERE id = ?').run(req.user.sub);
  res.json({ ok: true });
});

const port = Number(process.env.PORT || 8080);
app.listen(port, () => {
  console.log(`TextPort backend listening on :${port}`);
});
