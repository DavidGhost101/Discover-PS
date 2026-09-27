// Unit tests for web/js/access.js (the same rules the Android SchoolAccessTest covers).
import { test } from 'node:test';
import assert from 'node:assert/strict';
import * as A from '../../web/js/access.js';

const ts = (days) => new Date(Date.now() + days * 86_400_000);
const notices = [
  { id: 'all', targetRole: 'ALL', published: true, createdAt: ts(-3) },
  { id: 'parents', targetRole: 'PARENT', published: true, createdAt: ts(-1) },
  { id: 'parents-c1', targetRole: 'PARENT', classId: 'c1', published: true, createdAt: ts(-2) },
  { id: 'parents-c2', targetRole: 'PARENT', classId: 'c2', published: true },
  { id: 'teachers', targetRole: 'TEACHER', published: true },
  { id: 'draft', targetRole: 'ALL', published: false },
  { id: 'my-draft', targetRole: 'STUDENT', classId: 'c9', published: false, authorId: 'teacherA' },
];

test('parent sees school-wide, parent and own-children class notices only', () => {
  assert.deepEqual(A.visibleNotices(notices, 'PARENT', 'p', ['c1']).map((n) => n.id), ['parents', 'parents-c1', 'all']);
});

test('teacher sees teacher notices and own drafts, not parent notices', () => {
  assert.deepEqual(new Set(A.visibleNotices(notices, 'TEACHER', 'teacherA', ['c1']).map((n) => n.id)), new Set(['all', 'teachers', 'my-draft']));
});

test('drafts hidden from everyone but admin and author', () => {
  for (const role of ['TEACHER', 'PARENT', 'STUDENT', 'STAFF']) {
    assert.ok(!A.visibleNotices(notices, role, 'x', ['c1', 'c2']).some((n) => n.id === 'draft'), role);
  }
  assert.ok(A.visibleNotices(notices, 'ADMIN', 'a', []).some((n) => n.id === 'draft'));
});

test('assignments limited to classes, sorted by due date, undated last', () => {
  const list = [
    { id: 'late', classId: 'c1', dueDate: ts(5) }, { id: 'soon', classId: 'c1', dueDate: ts(1) },
    { id: 'undated', classId: 'c1', dueDate: null }, { id: 'other', classId: 'c2', dueDate: ts(0) },
  ];
  assert.deepEqual(A.assignmentsForClasses(list, ['c1']).map((a) => a.id), ['soon', 'late', 'undated']);
});

test('children and teacher classes', () => {
  const students = [{ uid: 's1', firstName: 'Zed', parentIds: ['pA'] }, { uid: 's2', firstName: 'Amy', parentIds: ['pB'] }, { uid: 's3', firstName: 'Ann', parentIds: ['pA', 'pB'] }];
  assert.deepEqual(A.childrenOf('pA', students).map((s) => s.uid), ['s3', 's1']);
  assert.deepEqual(A.childrenOf('stranger', students), []);
  const classes = [{ id: 'c1', name: 'B' }, { id: 'c2', name: 'A' }];
  assert.deepEqual(A.teacherClasses({ classIds: ['c1', 'c2'] }, classes).map((c) => c.id), ['c2', 'c1']);
  assert.deepEqual(A.teacherClasses(null, classes), []);
});

test('due status', () => {
  const now = new Date(2026, 2, 10, 12);
  assert.equal(A.dueStatus(null, now), A.DUE.NO_DUE_DATE);
  assert.equal(A.dueStatus(new Date(2026, 2, 10, 23), now), A.DUE.DUE_TODAY);
  assert.equal(A.dueStatus(new Date(2026, 2, 9, 23), now), A.DUE.OVERDUE);
  assert.equal(A.dueStatus(new Date(2026, 2, 11, 1), now), A.DUE.UPCOMING);
});

test('relationship diff', () => {
  assert.deepEqual(A.diff(['a', 'b', ''], ['b', 'c', 'c']), { added: ['c'], removed: ['a'] });
});

test('validation', () => {
  assert.equal(A.validate.email('parent@example.org'), null);
  assert.ok(A.validate.email('nope'));
  assert.equal(A.validate.password('Secure123'), null);
  assert.ok(A.validate.password('short1'));
  assert.ok(A.validate.password('onlyletters'));
  assert.equal(A.validate.phone(''), null);
  assert.equal(A.validate.phone('+27 82 123 4567'), null);
  assert.ok(A.validate.phone('12ab'));
  assert.equal(A.parseRole(' admin '), 'ADMIN');
  assert.equal(A.parseRole('superuser'), null);
  assert.equal(A.normalizeSouthAfricanPhone('082 123 4567'), '+27821234567');
});

test('date input round trip keeps the local day', () => {
  const d = A.fromDateInput('2026-10-15');
  assert.equal(A.toDateInput(d), '2026-10-15');
  assert.equal(d.getHours(), 23);
  assert.equal(A.fromDateInput(''), null);
});
