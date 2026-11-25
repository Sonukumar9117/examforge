import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Download, FileUp } from 'lucide-react';
import { useState } from 'react';
import { errorMessage } from '@/api/client';
import { adminQuestionApi } from '@/api/endpoints';
import type { ImportReport } from '@/api/types';
import { Button } from '@/components/ui/Button';
import { Alert } from '@/components/ui/Feedback';
import { Modal } from '@/components/ui/Modal';

const CSV_TEMPLATE = `subject,topic,type,difficulty,question,optionA,optionB,optionC,optionD,correct,explanation,marks,negativeMarks,tags
Physics,Units,MCQ,EASY,"What is the SI unit of force?",Newton,Joule,Watt,Pascal,A,"Force is measured in newtons (N).",1,0.25,units;basics
Indian Polity,Constitution,MULTI_SELECT,MEDIUM,"Which of these are Fundamental Rights?",Right to Equality,Right to Property,Right against Exploitation,Right to Work,A;C,"Right to Property is a legal right since 1978.",2,0.66,constitution
`;

/** JSON accepts an array (or one object). correctAnswer matches option ids; incorrect marks become the penalty. */
const JSON_TEMPLATE = `${JSON.stringify(
  [
    {
      id: 'NEET-MOCK-001',
      subject: 'Physics',
      topic: 'Electrostatics',
      difficulty: 'Easy',
      question: 'The SI unit of electric charge is:',
      options: [
        { id: 'A', text: 'Ampere' },
        { id: 'B', text: 'Coulomb' },
        { id: 'C', text: 'Volt' },
        { id: 'D', text: 'Ohm' },
      ],
      correctAnswer: 'B',
      explanation: 'Electric charge is measured in coulombs.',
      marks: { correct: 4, incorrect: -1, unattempted: 0 },
    },
    {
      id: 'NEET-MOCK-002',
      subject: 'Physics',
      topic: 'Vectors',
      difficulty: 'Medium',
      question: 'Which of the following are vector quantities?',
      options: [
        { id: 'A', text: 'Velocity' },
        { id: 'B', text: 'Mass' },
        { id: 'C', text: 'Force' },
        { id: 'D', text: 'Temperature' },
      ],
      correctAnswer: ['A', 'C'],
      explanation: 'Velocity and force have both magnitude and direction.',
      marks: { correct: 4, incorrect: -1, unattempted: 0 },
    },
  ],
  null,
  2,
)}\n`;

const MAX_BYTES = 2_000_000;

function download(name: string, text: string, type: string) {
  const url = URL.createObjectURL(new Blob([text], { type }));
  const a = document.createElement('a');
  a.href = url;
  a.download = name;
  a.click();
  URL.revokeObjectURL(url);
}

/** Two steps: check the file (dry run, nothing saved) → import the valid rows as drafts. */
export function ImportQuestionsDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [file, setFile] = useState<{ name: string; format: 'CSV' | 'JSON'; content: string } | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setFile(null);
    setReport(null);
    setError(null);
  };
  const close = () => {
    reset();
    onClose();
  };

  type Picked = { name: string; format: 'CSV' | 'JSON'; content: string };
  const run = useMutation({
    mutationFn: ({ f, dryRun }: { f: Picked; dryRun: boolean }) => adminQuestionApi.importFile(f.format, f.content, dryRun),
    onSuccess: (r) => {
      setReport(r);
      if (!r.dryRun) {
        queryClient.invalidateQueries({ queryKey: ['admin', 'questions'] });
        queryClient.invalidateQueries({ queryKey: ['admin', 'subjects'] });
      }
    },
    onError: (e) => setError(errorMessage(e)),
  });

  const onFile = async (f: File | undefined) => {
    reset();
    if (!f) return;
    if (f.size > MAX_BYTES) return setError('The file is larger than 2 MB. Split it into smaller files.');
    const lower = f.name.toLowerCase();
    const format = lower.endsWith('.json') ? 'JSON' : lower.endsWith('.csv') ? 'CSV' : null;
    if (!format) return setError('Choose a .csv or .json file.');
    const picked = { name: f.name, format, content: await f.text() } as const;
    setFile(picked);
    run.mutate({ f: picked, dryRun: true });
  };

  const checked = report?.dryRun;
  const finished = report && !report.dryRun;

  return (
    <Modal
      open={open}
      title="Import questions"
      onClose={close}
      size="lg"
      footer={
        finished ? (
          <Button onClick={close}>Done</Button>
        ) : (
          <>
            <Button variant="secondary" onClick={close}>
              Cancel
            </Button>
            <Button
              disabled={!checked || report.validRows === 0}
              loading={run.isPending && !!checked}
              onClick={() => file && run.mutate({ f: file, dryRun: false })}
            >
              Import {report?.validRows ?? 0} valid question{report?.validRows === 1 ? '' : 's'}
            </Button>
          </>
        )
      }
    >
      <div className="space-y-4 text-sm">
        <p className="muted">
          Upload a <strong>CSV</strong> or <strong>JSON</strong> file (max 1000 questions). The file is checked first; nothing
          is saved until you confirm. Imported questions are saved as <strong>Draft</strong> so you can review them before
          they are used in an exam.
        </p>
        <div className="flex flex-wrap gap-2">
          <Button size="sm" variant="ghost" onClick={() => download('examforge-questions-template.csv', CSV_TEMPLATE, 'text/csv')}>
            <Download className="h-4 w-4" /> CSV template
          </Button>
          <Button size="sm" variant="ghost" onClick={() => download('examforge-questions-template.json', JSON_TEMPLATE, 'application/json')}>
            <Download className="h-4 w-4" /> JSON template
          </Button>
        </div>

        <label className="flex cursor-pointer flex-col items-center gap-2 rounded-2xl border-2 border-dashed border-slate-300 px-4 py-8 text-center hover:border-brand-400 dark:border-gray-600">
          <FileUp className="h-8 w-8 text-brand-600" />
          <span className="font-semibold">{file ? file.name : 'Choose a file'}</span>
          <span className="text-xs muted">.csv or .json · up to 2 MB</span>
          <input
            type="file"
            accept=".csv,.json,text/csv,application/json"
            className="sr-only"
            onChange={(e) => {
              onFile(e.target.files?.[0]);
              e.target.value = '';
            }}
          />
        </label>

        {error && <Alert tone="error">{error}</Alert>}
        {run.isPending && !report && <p className="muted">Checking file…</p>}

        {report && (
          <div className="space-y-3">
            {finished ? (
              <Alert tone="success">
                Imported {report.imported} question{report.imported === 1 ? '' : 's'} as drafts.
                {report.errors.length > 0 && ` ${report.errors.length} row(s) were skipped.`}
              </Alert>
            ) : (
              <Alert tone={report.errors.length ? 'warning' : 'success'}>
                {report.validRows} of {report.totalRows} rows are valid.
                {report.errors.length > 0 && ' Rows with problems will be skipped — fix them and import again later.'}
              </Alert>
            )}
            {report.errors.length > 0 && (
              <div className="max-h-60 overflow-y-auto rounded-xl border border-slate-200 dark:border-gray-700">
                <table className="w-full text-left text-xs">
                  <thead className="sticky top-0 bg-slate-50 dark:bg-gray-800">
                    <tr>
                      <th className="px-3 py-2 font-semibold">{file?.format === 'CSV' ? 'Line' : 'Item'}</th>
                      <th className="px-3 py-2 font-semibold">Problem</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 dark:divide-gray-700">
                    {report.errors.map((e, i) => (
                      <tr key={i}>
                        <td className="px-3 py-2 font-mono">{e.row}</td>
                        <td className="px-3 py-2">{e.message}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
        )}
      </div>
    </Modal>
  );
}
