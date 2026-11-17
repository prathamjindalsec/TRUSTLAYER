import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';

import { EmptyState } from '../common/EmptyState';
import { titleCase } from '../../utils/formatters';

const chartColors = { LOW: '#1fa968', MEDIUM: '#d97706', HIGH: '#ea580c', CRITICAL: '#c2413c' };

export function RiskDistributionChart({ data }) {
  const chartData = (data ?? []).map((item) => ({ label: titleCase(item.riskClassification), key: item.riskClassification, count: item.count }));
  const hasData = chartData.some((item) => item.count > 0);
  if (!hasData) return <EmptyState title="No risk results yet" description="Risk distribution will appear after you run a URL, QR, or transaction simulation." />;
  return <div className="h-72 w-full" aria-label="Risk classification distribution chart"><ResponsiveContainer width="100%" height="100%"><BarChart data={chartData} margin={{ top: 8, right: 8, left: -20, bottom: 4 }}><CartesianGrid vertical={false} strokeDasharray="3 3" stroke="currentColor" opacity={0.12} /><XAxis dataKey="label" axisLine={false} tickLine={false} tick={{ fill: 'currentColor', fontSize: 12, opacity: 0.7 }} /><YAxis allowDecimals={false} axisLine={false} tickLine={false} tick={{ fill: 'currentColor', fontSize: 12, opacity: 0.7 }} /><Tooltip cursor={{ fill: 'rgba(31, 169, 104, 0.07)' }} contentStyle={{ borderRadius: 12, borderColor: '#cbd5e1', color: '#0f172a' }} formatter={(value) => [value, 'Assessments']} /><Bar dataKey="count" radius={[7, 7, 0, 0]} maxBarSize={54}>{chartData.map((item) => <Cell key={item.key} fill={chartColors[item.key]} />)}</Bar></BarChart></ResponsiveContainer></div>;
}
