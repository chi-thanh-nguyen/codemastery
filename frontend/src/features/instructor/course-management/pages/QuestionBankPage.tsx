import { useState } from 'react';
import { Box, Button, MenuItem, TextField, Typography } from '@mui/material';
import { DataTable, TableToolbar } from '../../../../shared/components/DataTable';
import { EmptyView } from '../../../../shared/components/StatusViews';
import { SearchPill } from '../../../../shared/components/PageHeader';
import { StatusPill } from '../../../../shared/components/StatusPill';
import { CircleAction } from '../../../../shared/components/Tile';
import { PresentationIcon } from '../../../../shared/components/PresentationIcon';
import { QuestionForm, emptyQuestion } from '../components/QuestionForm';
import { difficultyLabel, questionKindLabel } from '../types/courseManagementTypes';
import type { Difficulty, QuestionDraft, SkillOption } from '../types/courseManagementTypes';

export interface QuestionBankPageProps { questions: QuestionDraft[]; skills: SkillOption[]; onSave: (question: QuestionDraft) => void; headingLevel?: 'h1' | 'h2' }

const difficultyTone: Record<Difficulty, 'sky' | 'butter' | 'rose'> = { easy: 'sky', medium: 'butter', hard: 'rose' };

/** S19 Question bank: skill- and difficulty-tagged questions (US-11). Usable inside the course editor. */
export function QuestionBankPage({ questions, skills, onSave, headingLevel = 'h2' }: QuestionBankPageProps) {
    const [query, setQuery] = useState(''), [skill, setSkill] = useState('all'), [difficulty, setDifficulty] = useState<'all' | Difficulty>('all');
    const [editing, setEditing] = useState<QuestionDraft | null>(null);
    const skillName = (id: string) => skills.find((item) => item.id === id)?.name ?? 'Unmapped';
    const rows = questions.filter((question) => (skill === 'all' || question.skillId === skill) && (difficulty === 'all' || question.difficulty === difficulty)
        && question.prompt.toLowerCase().includes(query.trim().toLowerCase()));
    return <Box component="section" aria-labelledby="question-bank-title">
        <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, flexWrap: 'wrap', mb: 2 }}>
            <Typography id="question-bank-title" component={headingLevel} variant="h2">Question bank</Typography>
            <Button variant="contained" onClick={() => setEditing(emptyQuestion(skill === 'all' ? '' : skill))} startIcon={<PresentationIcon name="plus" />}>New question</Button>
        </Box>
        <TableToolbar count={`${rows.length} of ${questions.length} questions`}>
            <Box sx={{ flex: '1 1 240px', maxWidth: 360 }}><SearchPill label="Search questions" value={query} onChange={setQuery} onClear={() => setQuery('')} /></Box>
            <TextField select label="Skill" value={skill} onChange={(event) => setSkill(event.target.value)} sx={{ minWidth: 180, '& .MuiInputLabel-root': { position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' } }}>
                <MenuItem value="all">All skills</MenuItem>{skills.map((item) => <MenuItem key={item.id} value={item.id}>{item.name}</MenuItem>)}
            </TextField>
            <TextField select label="Difficulty" value={difficulty} onChange={(event) => setDifficulty(event.target.value as 'all' | Difficulty)} sx={{ minWidth: 160, '& .MuiInputLabel-root': { position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' } }}>
                <MenuItem value="all">All difficulties</MenuItem><MenuItem value="easy">Easy</MenuItem><MenuItem value="medium">Medium</MenuItem><MenuItem value="hard">Hard</MenuItem>
            </TextField>
        </TableToolbar>
        <DataTable label="Questions" rows={rows} getRowId={(question) => question.id}
            empty={<EmptyView icon="quiz" title={questions.length ? 'No questions match these filters' : 'No questions yet'} message={questions.length ? 'Try another skill, difficulty or search.' : 'Add questions tagged with a skill and difficulty. Quizzes draw from this bank.'} />}
            columns={[
                { key: 'prompt', header: 'Question', primary: true, render: (question) => <Typography sx={{ fontWeight: 600, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>{question.prompt}</Typography> },
                { key: 'kind', header: 'Type', render: (question) => questionKindLabel[question.kind] },
                { key: 'skill', header: 'Skill', render: (question) => skillName(question.skillId) },
                { key: 'difficulty', header: 'Difficulty', render: (question) => <StatusPill size="tag" tone={difficultyTone[question.difficulty]}>{difficultyLabel[question.difficulty]}</StatusPill> },
                { key: 'used', header: 'Used in', render: (question) => question.usedInQuizzes ? `${question.usedInQuizzes} ${question.usedInQuizzes === 1 ? 'quiz' : 'quizzes'}` : 'Not used yet' },
            ]}
            actions={(question) => <CircleAction label={`Edit question: ${question.prompt.slice(0, 40)}`} icon="edit" variant="fill" onClick={() => setEditing(question)} />} />
        {editing && <QuestionForm key={editing.id || 'new'} open initial={editing} skills={skills} onCancel={() => setEditing(null)} onSave={(question) => { onSave(question); setEditing(null); }} />}
    </Box>;
}
