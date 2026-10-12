import { useCallback, useEffect, useMemo, useState, type FormEvent, type ReactNode } from 'react';
import {
  Activity, ArrowDownLeft, ArrowRight, ArrowUpRight, BellRing, CheckCircle2,
  CircleAlert, Clock3, Copy, CreditCard, ExternalLink, FileClock, Globe2,
  LayoutDashboard, LoaderCircle, LogOut, Plus, RefreshCw, Search, ShieldCheck,
  Wallet, Webhook, X
} from 'lucide-react';
import type { AuditEvent, DashboardSummary, LedgerEntry, Payment, PaymentStatus, WebhookDelivery, WebhookEndpoint } from './types';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
type Tab = 'overview' | 'payments' | 'ledger' | 'webhooks' | 'audit';
type AuthMode = 'login' | 'register';

class ApiError extends Error {
  status: number;
  constructor(message: string, status: number) { super(message); this.status = status; }
}

export default function App() {
  const [token, setToken] = useState<string | null>(() => localStorage.getItem('fluxpay_token'));
  const [username, setUsername] = useState(() => localStorage.getItem('fluxpay_username') || '');
  const [tab, setTab] = useState<Tab>('overview');
  const [authMode, setAuthMode] = useState<AuthMode>('login');
  const [authUsername, setAuthUsername] = useState('');
  const [authPassword, setAuthPassword] = useState('');
  const [payments, setPayments] = useState<Payment[]>([]);
  const [ledger, setLedger] = useState<LedgerEntry[]>([]);
  const [webhooks, setWebhooks] = useState<WebhookEndpoint[]>([]);
  const [deliveries, setDeliveries] = useState<WebhookDelivery[]>([]);
  const [audit, setAudit] = useState<AuditEvent[]>([]);
  const [summary, setSummary] = useState<DashboardSummary | null>(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | PaymentStatus>('ALL');
  const [amount, setAmount] = useState('1250.00');
  const [currency, setCurrency] = useState('INR');
  const [customerEmail, setCustomerEmail] = useState('customer@example.com');
  const [webhookUrl, setWebhookUrl] = useState('');
  const [newWebhookSecret, setNewWebhookSecret] = useState('');

  const api = useCallback(async <T,>(path: string, options: RequestInit = {}, bearer: string | null = token): Promise<T> => {
    const headers = new Headers(options.headers);
    headers.set('Content-Type', 'application/json');
    if (bearer) headers.set('Authorization', `Bearer ${bearer}`);
    const response = await fetch(`${API_BASE}${path}`, { ...options, headers });
    const text = await response.text();
    let data: unknown = undefined;
    if (text) { try { data = JSON.parse(text); } catch { data = text; } }
    if (!response.ok) {
      const record = typeof data === 'object' && data !== null ? data as Record<string, unknown> : {};
      throw new ApiError(String(record.message || record.error || `Request failed (${response.status})`), response.status);
    }
    return data as T;
  }, [token]);

  const refreshWorkspace = useCallback(async () => {
    if (!token) return;
    setLoading(true);
    setError('');
    try {
      const results = await Promise.allSettled([
        api<Payment[]>('/api/v1/payments'),
        api<LedgerEntry[]>('/api/v1/ledger'),
        api<WebhookEndpoint[]>('/api/v1/webhooks'),
        api<WebhookDelivery[]>('/api/v1/webhooks/deliveries'),
        api<AuditEvent[]>('/api/v1/audit'),
        api<DashboardSummary>('/api/v1/dashboard/summary'),
      ]);
      const failures = results.filter((result) => result.status === 'rejected');
      if (failures.length && failures.some((result) => result.status === 'rejected' && result.reason instanceof ApiError && result.reason.status === 401)) {
        setToken(null); localStorage.removeItem('fluxpay_token');
        throw new Error('Your session expired. Please sign in again.');
      }
      if (results[0].status === 'fulfilled') setPayments(results[0].value);
      if (results[1].status === 'fulfilled') setLedger(results[1].value);
      if (results[2].status === 'fulfilled') setWebhooks(results[2].value);
      if (results[3].status === 'fulfilled') setDeliveries(results[3].value);
      if (results[4].status === 'fulfilled') setAudit(results[4].value);
      if (results[5].status === 'fulfilled') setSummary(results[5].value);
      if (failures.length) {
        const first = failures.find((result) => result.status === 'rejected');
        if (first?.status === 'rejected') setError(`Some panels could not load: ${first.reason instanceof Error ? first.reason.message : 'API unavailable'}`);
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load FluxPay data. Check that the backend is running.');
    } finally { setLoading(false); }
  }, [api, token]);

  useEffect(() => { if (token) void refreshWorkspace(); }, [token, refreshWorkspace]);

  const filteredPayments = useMemo(() => payments.filter((payment) => {
    const matchesStatus = statusFilter === 'ALL' || payment.status === statusFilter;
    const query = search.trim().toLowerCase();
    const matchesSearch = !query || payment.id.toLowerCase().includes(query) || payment.customerEmail.toLowerCase().includes(query);
    return matchesStatus && matchesSearch;
  }), [payments, search, statusFilter]);

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      if (authMode === 'register') {
        const result = await api<{ message: string }>('/api/v1/auth/register', {
          method: 'POST', body: JSON.stringify({ username: authUsername, password: authPassword })
        }, null);
        setNotice(`${result.message}. Sign in with your new account.`); setAuthMode('login'); setAuthPassword('');
      } else {
        const result = await api<{ token: string }>('/api/v1/auth/login', {
          method: 'POST', body: JSON.stringify({ username: authUsername, password: authPassword })
        }, null);
        localStorage.setItem('fluxpay_token', result.token);
        localStorage.setItem('fluxpay_username', authUsername.trim());
        setUsername(authUsername.trim()); setToken(result.token); setAuthPassword('');
      }
    } catch (e) { setError(e instanceof Error ? e.message : 'Authentication failed.'); }
    finally { setBusy(false); }
  }

  async function createPayment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(''); setNotice('');
    try {
      const key = crypto.randomUUID();
      const payment = await api<Payment>('/api/v1/payments', {
        method: 'POST', headers: { 'Idempotency-Key': key },
        body: JSON.stringify({ amount: Number(amount), currency, customerEmail })
      });
      setNotice(`Payment ${payment.id.slice(0, 8)} created. Run reconciliation to simulate provider processing.`);
      setTab('payments'); await refreshWorkspace();
    } catch (e) { setError(e instanceof Error ? e.message : 'Payment could not be created.'); }
    finally { setBusy(false); }
  }

  async function runReconciliation() {
    setBusy(true); setError(''); setNotice('');
    try {
      const result = await api<{ processed: number; completed: number; failed: number }>('/api/v1/reconciliation/run', { method: 'POST' });
      setNotice(`Simulation finished: ${result.processed} processed · ${result.completed} completed · ${result.failed} failed.`);
      await refreshWorkspace();
    } catch (e) { setError(e instanceof Error ? e.message : 'Reconciliation failed.'); }
    finally { setBusy(false); }
  }

  async function registerWebhook(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError(''); setNotice(''); setNewWebhookSecret('');
    try {
      const result = await api<{ signingSecret: string; targetUrl: string }>('/api/v1/webhooks', {
        method: 'POST', body: JSON.stringify({ targetUrl: webhookUrl })
      });
      setNewWebhookSecret(result.signingSecret); setWebhookUrl('');
      setNotice(`Webhook created for ${result.targetUrl}. Copy the signing secret now; it will not be shown again.`);
      await refreshWorkspace();
    } catch (e) { setError(e instanceof Error ? e.message : 'Webhook could not be created.'); }
    finally { setBusy(false); }
  }

  function logout() {
    localStorage.removeItem('fluxpay_token'); localStorage.removeItem('fluxpay_username');
    setToken(null); setPayments([]); setLedger([]); setWebhooks([]); setDeliveries([]); setAudit([]); setSummary(null);
    setNotice(''); setError('');
  }

  if (!token) {
    return <main className="auth-shell">
      <div className="auth-glow auth-glow-one" /><div className="auth-glow auth-glow-two" />
      <div className="auth-left">
        <Brand />
        <div className="auth-copy">
          <div className="eyebrow"><span className="eyebrow-dot" /> PAYMENT OPERATIONS PLATFORM</div>
          <h1>Money movement,<br /><span>made observable.</span></h1>
          <p>A clean control room for payment events, reconciliation, and your transaction ledger.</p>
          <div className="auth-proof-row"><div className="proof-icon"><ShieldCheck size={18} /></div><div><strong>Built with safety in mind</strong><span>JWT auth · Idempotency · Auditable flows</span></div></div>
        </div>
        <div className="auth-foot">FLUXPAY PLATFORM <span>•</span> LOCAL DEMO ENVIRONMENT</div>
      </div>
      <section className="auth-card">
        <div className="auth-card-top"><div className="auth-mini-icon"><ShieldCheck size={18} /></div><span>WORKSPACE ACCESS</span></div>
        <h2>{authMode === 'login' ? 'Welcome back' : 'Create your workspace'}</h2>
        <p className="subtle">{authMode === 'login' ? 'Sign in to continue to your payment workspace.' : 'Create a local demo account to get started.'}</p>
        {error && <Notice kind="error" message={error} />}{notice && <Notice kind="success" message={notice} />}
        <form className="auth-form" onSubmit={submitAuth}>
          <label>Username<input value={authUsername} onChange={(e) => setAuthUsername(e.target.value)} autoComplete="username" required minLength={3} maxLength={50} placeholder="e.g. prasanna" /></label>
          <label>Password<input value={authPassword} onChange={(e) => setAuthPassword(e.target.value)} autoComplete={authMode === 'login' ? 'current-password' : 'new-password'} type="password" required minLength={8} maxLength={100} placeholder="At least 8 characters" /></label>
          <button className="button button-primary full-width" disabled={busy} type="submit">{busy ? <LoaderCircle className="spin" size={17} /> : null}{authMode === 'login' ? 'Sign in securely' : 'Create account'}<ArrowRight size={16} /></button>
        </form>
        <div className="auth-switch">{authMode === 'login' ? 'New to FluxPay?' : 'Already have an account?'} <button type="button" onClick={() => { setAuthMode(authMode === 'login' ? 'register' : 'login'); setError(''); setNotice(''); }}>{authMode === 'login' ? 'Create an account' : 'Sign in'}</button></div>
        <div className="auth-security"><LockIcon /> Your password is hashed on the server. This demo does not connect to a real payment network.</div>
      </section>
    </main>;
  }

  const completed = payments.filter((p) => p.status === 'COMPLETED');
  const pending = payments.filter((p) => p.status === 'PENDING' || p.status === 'PROCESSING').length;
  const totalEntries = ledger.length;
  const mainCurrency = Object.keys(summary?.completedVolumeByCurrency || {})[0] || 'INR';
  const mainVolume = summary?.completedVolumeByCurrency?.[mainCurrency] || 0;

  return <div className="app-shell">
    <aside className="sidebar">
      <Brand />
      <div className="workspace-switcher"><div className="workspace-avatar">{(username || 'F').slice(0, 1).toUpperCase()}</div><div className="workspace-label"><strong>{username || 'My workspace'}</strong><span>Personal workspace</span></div><span className="online-dot" /></div>
      <div className="nav-label">WORKSPACE</div>
      <nav className="side-nav">
        <NavItem active={tab === 'overview'} icon={<LayoutDashboard size={18} />} label="Overview" onClick={() => setTab('overview')} />
        <NavItem active={tab === 'payments'} icon={<CreditCard size={18} />} label="Payments" count={payments.length} onClick={() => setTab('payments')} />
        <NavItem active={tab === 'ledger'} icon={<Wallet size={18} />} label="Ledger" count={ledger.length} onClick={() => setTab('ledger')} />
        <NavItem active={tab === 'webhooks'} icon={<Webhook size={18} />} label="Webhooks" count={webhooks.length} onClick={() => setTab('webhooks')} />
        <NavItem active={tab === 'audit'} icon={<FileClock size={18} />} label="Audit trail" onClick={() => setTab('audit')} />
      </nav>
      <div className="sidebar-bottom">
        <div className="environment-card"><span className="environment-pulse" /><div><strong>Sandbox mode</strong><small>Provider is simulated</small></div><span className="sandbox-pill">TEST</span></div>
        <a className="sidebar-docs" href={`${API_BASE}/swagger-ui.html`} target="_blank" rel="noreferrer"><Globe2 size={16} /> API documentation <ExternalLink size={13} /></a>
        <button className="user-footer" onClick={logout}><div className="user-avatar">{(username || 'U').slice(0, 1).toUpperCase()}</div><div><strong>{username}</strong><span>Signed in</span></div><LogOut size={16} /></button>
      </div>
    </aside>

    <main className="main-area">
      <header className="topbar">
        <div className="breadcrumbs"><span>Workspace</span><span className="crumb-slash">/</span><strong>{tabLabel(tab)}</strong></div>
        <div className="topbar-right"><div className="status-healthy"><span /> API workspace</div><button className="icon-button" title="Refresh data" onClick={() => void refreshWorkspace()} disabled={loading}><RefreshCw size={17} className={loading ? 'spin' : ''} /></button><div className="topbar-avatar">{(username || 'F').slice(0, 1).toUpperCase()}</div></div>
      </header>
      <section className="page-content">
        <div className="page-heading"><div><div className="page-kicker">PAYMENT INFRASTRUCTURE <span>•</span> SANDBOX</div><h1>{tabLabel(tab)}<span className="title-period">.</span></h1><p>{tabDescription(tab)}</p></div><div className="heading-actions"><button className="button button-secondary" onClick={() => void runReconciliation()} disabled={busy}><Activity size={16} /> Run reconciliation</button><button className="button button-primary" onClick={() => setTab('payments')}><Plus size={17} /> New payment</button></div></div>
        {error && <Notice kind="error" message={error} onClose={() => setError('')} />}
        {notice && <Notice kind="success" message={notice} onClose={() => setNotice('')} />}
        {loading && payments.length === 0 && <div className="loading-state"><LoaderCircle className="spin" size={26} /><span>Loading your workspace…</span></div>}

        {tab === 'overview' && <>
          <div className="stat-grid">
            <StatCard label="Total payments" value={String(payments.length)} sub={`${payments.filter(p => p.status === 'COMPLETED').length} completed`} icon={<CreditCard size={18} />} tone="blue" trend="All-time" />
            <StatCard label="Processed volume" value={formatMoney(mainVolume, mainCurrency)} sub={`${completed.length} completed payments`} icon={<Wallet size={18} />} tone="green" trend={mainCurrency} />
            <StatCard label="Awaiting action" value={String(pending)} sub="Pending or processing" icon={<Clock3 size={18} />} tone="amber" trend="Live" />
            <StatCard label="Ledger entries" value={String(totalEntries)} sub="Double-entry records" icon={<ShieldCheck size={18} />} tone="purple" trend="Balanced pairs" />
          </div>
          <div className="overview-grid">
            <section className="panel panel-main"><PanelHeading title="Payment activity" description="Recent transactions across your workspace" action={<button className="text-action" onClick={() => setTab('payments')}>View all <ArrowRight size={14} /></button>} />
              <div className="table-wrap"><PaymentTable payments={payments.slice().sort((a,b) => b.createdAt.localeCompare(a.createdAt)).slice(0, 6)} onOpen={() => setTab('payments')} /></div>
            </section>
            <section className="panel health-panel"><PanelHeading title="System health" description="Local workspace status" />
              <div className="health-row"><div className="health-icon green"><CheckCircle2 size={17} /></div><div><strong>API workspace</strong><span>Authenticated session active</span></div><span className="health-tag healthy">Ready</span></div>
              <div className="health-row"><div className="health-icon blue"><Globe2 size={17} /></div><div><strong>Database layer</strong><span>PostgreSQL-backed persistence</span></div><span className="health-tag neutral">Configured</span></div>
              <div className="health-row"><div className="health-icon purple"><Activity size={17} /></div><div><strong>Event pipeline</strong><span>Transactional outbox + Kafka</span></div><span className="health-tag neutral">Configured</span></div>
              <div className="health-row"><div className="health-icon amber"><Webhook size={17} /></div><div><strong>Webhook delivery</strong><span>Retry queue with HMAC signatures</span></div><span className="health-tag neutral">{deliveries.filter(d => d.status === 'FAILED').length} failed</span></div>
              <div className="sandbox-note"><CircleAlert size={16} /><p>Provider outcomes are simulated. No real money is moved by this project.</p></div>
            </section>
          </div>
          <div className="bottom-grid">
            <section className="panel"><PanelHeading title="Status distribution" description="Current payment lifecycle" />
              <div className="status-bars">{(['COMPLETED', 'PENDING', 'PROCESSING', 'FAILED', 'CANCELLED'] as PaymentStatus[]).map((status) => { const count = payments.filter(p => p.status === status).length; const pct = payments.length ? count / payments.length * 100 : 0; return <div className="status-bar-row" key={status}><div><StatusBadge status={status} /><span>{count}</span></div><div className="bar-track"><div className={`bar-fill bar-${status.toLowerCase()}`} style={{ width: `${pct}%` }} /></div></div>; })}</div>
            </section>
            <section className="panel"><PanelHeading title="Quick actions" description="Common operational tasks" />
              <div className="quick-actions"><button onClick={() => setTab('payments')}><div className="quick-icon blue"><Plus size={18} /></div><div><strong>Create payment</strong><span>Start an idempotent payment</span></div><ArrowRight size={16} /></button><button onClick={() => void runReconciliation()} disabled={busy}><div className="quick-icon green"><RefreshCw size={18} /></div><div><strong>Reconcile payments</strong><span>Simulate provider outcomes</span></div><ArrowRight size={16} /></button><button onClick={() => setTab('webhooks')}><div className="quick-icon purple"><BellRing size={18} /></div><div><strong>Manage webhooks</strong><span>Inspect endpoints and delivery retries</span></div><ArrowRight size={16} /></button></div>
            </section>
          </div>
        </>}

        {tab === 'payments' && <div className="payments-layout">
          <section className="panel create-panel"><PanelHeading title="Create a payment" description="Creates a pending record with an idempotency key" />
            <form className="create-form" onSubmit={createPayment}><label>Amount<div className="amount-input"><span>{currency === 'INR' ? '₹' : currency === 'USD' ? '$' : currency}</span><input type="number" min="0.01" step="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required /></div></label>
              <label>Currency<select value={currency} onChange={(e) => setCurrency(e.target.value)}><option value="INR">INR · Indian Rupee</option><option value="USD">USD · US Dollar</option><option value="EUR">EUR · Euro</option><option value="GBP">GBP · Pound Sterling</option></select></label>
              <label>Customer email<input type="email" value={customerEmail} onChange={(e) => setCustomerEmail(e.target.value)} required maxLength={255} /></label>
              <div className="idempotency-info"><ShieldCheck size={17} /><span>A unique idempotency key is generated for every new submission to prevent duplicate processing on retries.</span></div>
              <button className="button button-primary full-width" type="submit" disabled={busy}>{busy ? <LoaderCircle className="spin" size={16} /> : <Plus size={16} />} Create payment</button>
            </form>
          </section>
          <section className="panel payments-table-panel"><PanelHeading title="Transactions" description={`${filteredPayments.length} payment${filteredPayments.length === 1 ? '' : 's'} in view`} />
            <div className="table-controls"><div className="search-field"><Search size={15} /><input value={search} onChange={(e) => setSearch(e.target.value)} placeholder="Search payment ID or email" /></div><select aria-label="Filter by status" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as 'ALL' | PaymentStatus)}><option value="ALL">All statuses</option>{(['PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'CANCELLED'] as PaymentStatus[]).map(s => <option value={s} key={s}>{s}</option>)}</select></div>
            <div className="table-wrap"><PaymentTable payments={filteredPayments.slice().sort((a,b) => b.createdAt.localeCompare(a.createdAt))} /></div>
          </section>
        </div>}

        {tab === 'ledger' && <section className="panel"><PanelHeading title="Double-entry ledger" description="Every completed payment posts a matched debit and credit" action={<span className="ledger-balance"><ShieldCheck size={15} /> Balanced entries</span>} />
          {ledger.length === 0 ? <EmptyState icon={<Wallet size={23} />} title="Your ledger is waiting" message="Completed payments create processor-clearing and merchant-payables entries automatically." /> : <div className="table-wrap"><table><thead><tr><th>ACCOUNT</th><th>DIRECTION</th><th>PAYMENT</th><th>AMOUNT</th><th>GROUP</th><th>CREATED</th></tr></thead><tbody>{ledger.map((entry) => <tr key={entry.id}><td><div className="table-primary">{entry.accountCode}</div><div className="table-secondary">{entry.entryKey.slice(-18)}</div></td><td><span className={`direction direction-${entry.direction.toLowerCase()}`}>{entry.direction === 'DEBIT' ? <ArrowDownLeft size={13} /> : <ArrowUpRight size={13} />}{entry.direction}</span></td><td><code className="mono-id">{entry.paymentId.slice(0, 8)}…</code></td><td className="amount-cell">{formatMoney(entry.amount, entry.currency)}</td><td><code className="mono-id">{entry.entryGroupId.slice(0, 8)}…</code></td><td>{formatDate(entry.createdAt)}</td></tr>)}</tbody></table></div>}
        </section>}

        {tab === 'webhooks' && <div className="webhooks-grid"><section className="panel create-panel"><PanelHeading title="Add endpoint" description="Receive signed event notifications" /><form className="create-form" onSubmit={registerWebhook}><label>Endpoint URL<input type="url" placeholder="https://your-domain.com/webhooks" value={webhookUrl} onChange={(e) => setWebhookUrl(e.target.value)} required maxLength={2048} /></label><div className="idempotency-info"><ShieldCheck size={17} /><span>HTTPS is required for public hosts. Delivery requests contain an HMAC SHA-256 signature in X-FluxPay-Signature.</span></div><button className="button button-primary full-width" type="submit" disabled={busy}><Plus size={16} /> Register endpoint</button></form>
          {newWebhookSecret && <div className="secret-reveal"><div className="secret-title"><ShieldCheck size={17} /> Signing secret · copy now</div><code>{newWebhookSecret}</code><button className="button button-secondary full-width" onClick={() => { void navigator.clipboard.writeText(newWebhookSecret); setNotice('Signing secret copied. Store it securely.'); }}><Copy size={15} /> Copy secret</button><small>FluxPay will not display this secret again.</small></div>}
        </section><section className="panel"><PanelHeading title="Registered endpoints" description={`${webhooks.length} configured`} />{webhooks.length === 0 ? <EmptyState icon={<Webhook size={23} />} title="No endpoints yet" message="Add a trusted endpoint to start receiving payment events." /> : <div className="endpoint-list">{webhooks.map(w => <div className="endpoint-item" key={w.id}><div className="endpoint-icon"><Webhook size={18} /></div><div className="endpoint-info"><strong>{w.targetUrl}</strong><span>Added {formatDate(w.createdAt)} · ID {w.id.slice(0, 8)}</span></div><span className={`health-tag ${w.active ? 'healthy' : 'neutral'}`}>{w.active ? 'Active' : 'Paused'}</span></div>)}</div>}
          <div className="subsection-heading"><div><h3>Recent deliveries</h3><p>Retry queue and response history</p></div><button className="icon-button" title="Refresh deliveries" onClick={() => void refreshWorkspace()}><RefreshCw size={15} /></button></div>{deliveries.length === 0 ? <p className="empty-inline">No webhook deliveries to display.</p> : <div className="delivery-list">{deliveries.slice(0, 8).map(d => <div className="delivery-item" key={d.id}><div className={`delivery-dot delivery-${d.status.toLowerCase()}`} /><div className="delivery-info"><strong>{d.eventType}</strong><span>{d.targetUrl} · {formatDate(d.createdAt)}</span></div><div className="delivery-meta"><StatusBadge status={d.status} /><small>{d.attempts} attempt{d.attempts === 1 ? '' : 's'}{d.responseStatus ? ` · ${d.responseStatus}` : ''}</small></div></div>)}</div>}
        </section></div>}

        {tab === 'audit' && <section className="panel"><PanelHeading title="Audit trail" description="Recent actions recorded for this authenticated account" />{audit.length === 0 ? <EmptyState icon={<FileClock size={23} />} title="No audit activity yet" message="Authentication and payment actions will be recorded here." /> : <div className="table-wrap"><table><thead><tr><th>EVENT</th><th>RESOURCE</th><th>DETAILS</th><th>TIME</th></tr></thead><tbody>{audit.map(item => <tr key={item.id}><td><div className="audit-action"><span className="audit-icon"><Activity size={14} /></span><div><strong>{item.action.replaceAll('_', ' ')}</strong><small>{item.actor}</small></div></div></td><td><span className="resource-pill">{item.resourceType}</span><div className="table-secondary">{item.resourceId || '—'}</div></td><td className="audit-detail">{item.details || '—'}</td><td>{formatDate(item.createdAt)}</td></tr>)}</tbody></table></div>}</section>}

        <footer className="page-footer"><span>© {new Date().getFullYear()} FluxPay</span><span><span className="footer-dot" /> Sandbox mode · Simulated payments only</span><a href={`${API_BASE}/swagger-ui.html`} target="_blank" rel="noreferrer">API reference <ExternalLink size={12} /></a></footer>
      </section>
    </main>
  </div>;
}

function Brand() { return <div className="brand-lockup"><div className="brand-mark"><span /><span /><span /></div><span className="brand-name">flux<span>pay</span></span><span className="brand-version">v1</span></div>; }
function LockIcon() { return <ShieldCheck size={15} />; }
function NavItem({ active, icon, label, count, onClick }: { active: boolean; icon: ReactNode; label: string; count?: number; onClick: () => void }) {
  return <button className={`nav-item ${active ? 'nav-active' : ''}`} onClick={onClick}>{icon}<span>{label}</span>{count !== undefined && count > 0 ? <small>{count > 99 ? '99+' : count}</small> : null}{active && <span className="nav-active-marker" />}</button>;
}
function StatCard({ label, value, sub, icon, tone, trend }: { label: string; value: string; sub: string; icon: ReactNode; tone: string; trend: string }) {
  return <div className="stat-card"><div className="stat-top"><div className={`stat-icon ${tone}`}>{icon}</div><span className="stat-trend">{trend}</span></div><div className="stat-label">{label}</div><div className="stat-value">{value}</div><div className="stat-sub">{sub}</div></div>;
}
function PanelHeading({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return <div className="panel-heading"><div><h2>{title}</h2><p>{description}</p></div>{action}</div>;
}
function PaymentTable({ payments, onOpen }: { payments: Payment[]; onOpen?: () => void }) {
  if (!payments.length) return <EmptyState icon={<CreditCard size={22} />} title="No payments yet" message="Create a test payment and run reconciliation to see it move through its lifecycle." />;
  return <table><thead><tr><th>PAYMENT</th><th>CUSTOMER</th><th>STATUS</th><th>AMOUNT</th><th>CREATED</th></tr></thead><tbody>{payments.map(payment => <tr key={payment.id} onClick={onOpen} className={onOpen ? 'clickable-row' : ''}><td><div className="payment-id-cell"><div className="payment-avatar"><CreditCard size={15} /></div><div><strong>{payment.id.slice(0, 8)}…</strong><small>Payment intent</small></div></div></td><td><span className="customer-email">{payment.customerEmail}</span></td><td><StatusBadge status={payment.status} /></td><td className="amount-cell">{formatMoney(payment.amount, payment.currency)}</td><td className="date-cell">{formatDate(payment.createdAt)}</td></tr>)}</tbody></table>;
}
function StatusBadge({ status }: { status: string }) { return <span className={`status-badge status-${status.toLowerCase()}`}><span />{status.replaceAll('_', ' ')}</span>; }
function Notice({ kind, message, onClose }: { kind: 'error' | 'success'; message: string; onClose?: () => void }) {
  return <div className={`notice notice-${kind}`}>{kind === 'error' ? <CircleAlert size={17} /> : <CheckCircle2 size={17} />}<span>{message}</span>{onClose && <button onClick={onClose} title="Dismiss"><X size={14} /></button>}</div>;
}
function EmptyState({ icon, title, message }: { icon: ReactNode; title: string; message: string }) {
  return <div className="empty-state"><div className="empty-icon">{icon}</div><strong>{title}</strong><p>{message}</p></div>;
}
function tabLabel(tab: Tab) { return ({ overview: 'Overview', payments: 'Payments', ledger: 'Ledger', webhooks: 'Webhooks', audit: 'Audit trail' } as const)[tab]; }
function tabDescription(tab: Tab) { return ({ overview: 'A real-time pulse on your payment operations.', payments: 'Create, search and inspect simulated payment transactions.', ledger: 'Inspect the balanced accounting entries posted on completion.', webhooks: 'Configure trusted endpoints and monitor signed delivery attempts.', audit: 'A chronological record of security and payment operations.' } as const)[tab]; }
function formatMoney(value: number, currency: string) {
  try { return new Intl.NumberFormat('en-IN', { style: 'currency', currency, maximumFractionDigits: 2 }).format(value); }
  catch { return `${value.toFixed(2)} ${currency}`; }
}
function formatDate(value: string) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }).format(date);
}
