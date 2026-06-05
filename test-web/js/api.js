const API_BASE_KEY = 'risklendpro_api_base';
const USER_TOKEN_KEY = 'risklendpro_user_token';
const ADMIN_TOKEN_KEY = 'risklendpro_admin_token';

const DEFAULT_API_BASE = 'http://localhost:8080/api/v1';

const SCORE_THRESHOLDS = {
  min: 350,
  max: 950,
  rejectBelow: 642,
  approveFrom: 788,
};

const EXTERNAL_FEATURE_LABELS = {
  linked: '已关联外部数据',
  daysBirth: '出生天数',
  daysEmployed: '就业天数',
  amtIncomeTotal: '年收入总额',
  creditBureauWeek: '近1周征信查询',
  creditBureauMon: '近1月征信查询',
  daysLastPhoneChange: '上次换号天数',
  activeLoansCount: '活跃贷款数',
  extSource2: '外部权威分2',
  extSource3: '外部权威分3',
  flagOwnCar: '有车标记',
  occupationType: '职业类型',
  educationType: '学历类型',
  target: '历史违约标签',
  prevRefusedCount: '历史被拒次数',
  dataSource: '数据来源',
  updatedAt: '数据更新时间',
};

const SYS_DECISION_LABELS = {
  APPROVE: '自动通过',
  MANUAL_REVIEW: '人工审核',
  REJECT: '自动拒绝',
};

function sysDecisionLabel(code) {
  return SYS_DECISION_LABELS[code] || code || '-';
}

function externalFeatureLabel(key) {
  return EXTERNAL_FEATURE_LABELS[key] || key;
}

function describeScoreZone(score, thresholds) {
  const t = thresholds || SCORE_THRESHOLDS;
  const s = Number(score);
  if (Number.isNaN(s)) return '';
  if (s < t.rejectBelow) {
    return `${Math.round(s)} 分，低于拒绝线 ${t.rejectBelow}（自动拒绝档）`;
  }
  if (s < t.approveFrom) {
    const gap = t.approveFrom - s;
    return `${Math.round(s)} 分，处于人工审核档（${t.rejectBelow}–${t.approveFrom - 1}），距自动通过还差 ${Math.round(gap)} 分`;
  }
  return `${Math.round(s)} 分，达到或超过通过线 ${t.approveFrom}（自动通过档）`;
}

function scoreZoneFromScore(score, thresholds) {
  const t = thresholds || SCORE_THRESHOLDS;
  const s = Number(score);
  if (Number.isNaN(s)) return null;
  if (s < t.rejectBelow) return 'REJECT';
  if (s < t.approveFrom) return 'MANUAL';
  return 'APPROVE';
}

const MATERIAL_LABELS = {
  ID_CARD_FRONT: '身份证正面',
  ID_CARD_BACK: '身份证反面',
  RESIDENCE_PROOF: '户籍或居住证明',
  INCOME_PROOF: '收入证明',
  EMPLOYMENT_PROOF: '工作证明',
  CREDIT_EXPLANATION: '信用情况说明',
};

function ruleTriggerLabel(code) {
  const labels = {
    BLACKLIST_L2: '二级黑名单',
    INCOME_OUTLIER: '收入异常',
    SCORE_ONLY: '信用分人工区间',
    'BLACKLIST_L2,INCOME_OUTLIER': '二级黑名单+收入异常',
  };
  return labels[code] || code || '-';
}

const REJECT_GATE_LABELS = {
  BLACKLIST_L3: '三级黑名单拒绝',
  INCOME_VERIFICATION: '收入验真失败（偏差>50%）',
  SCORE_LOW: '信用分过低（<642）',
  RULE_AND_SCORE_LOW: '规则命中+分数过低',
  SYSTEM_ERROR: '系统异常',
  UNKNOWN: '未知原因',
};

function rejectGateLabel(code) {
  return REJECT_GATE_LABELS[code] || code || '-';
}

const USER_DETAIL_LABELS = {
  name: '姓名',
  idCard: '身份证',
  education: '学历',
  marriage: '婚姻',
  jobType: '职业',
  monthlyIncome: '月收入区间',
  hasHouse: '有房',
  hasCar: '有车',
  age: '年龄',
};

function userDetailLabel(key) {
  return USER_DETAIL_LABELS[key] || externalFeatureLabel(key) || key;
}

function materialTypeLabel(code) {
  return MATERIAL_LABELS[code] || code || '-';
}

function getApiBase() {
  return localStorage.getItem(API_BASE_KEY) || DEFAULT_API_BASE;
}

function setApiBase(url) {
  const trimmed = url.replace(/\/$/, '');
  localStorage.setItem(API_BASE_KEY, trimmed);
  return trimmed;
}

function getToken(role) {
  return localStorage.getItem(role === 'ADMIN' ? ADMIN_TOKEN_KEY : USER_TOKEN_KEY) || '';
}

function setToken(role, token) {
  localStorage.setItem(role === 'ADMIN' ? ADMIN_TOKEN_KEY : USER_TOKEN_KEY, token || '');
}

function clearToken(role) {
  localStorage.removeItem(role === 'ADMIN' ? ADMIN_TOKEN_KEY : USER_TOKEN_KEY);
}

async function apiRequest(path, options = {}) {
  const base = getApiBase();
  const url = path.startsWith('http') ? path : `${base}${path.startsWith('/') ? path : '/' + path}`;
  const headers = { ...(options.headers || {}) };

  if (options.token) {
    headers['Authorization'] = `Bearer ${options.token}`;
  }

  if (options.body && !(options.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }

  const resp = await fetch(url, {
    method: options.method || 'GET',
    headers,
    body: options.body instanceof FormData
      ? options.body
      : options.body
        ? JSON.stringify(options.body)
        : undefined,
  });

  const contentType = resp.headers.get('content-type') || '';
  if (contentType.includes('application/json')) {
    const json = await resp.json();
    if (!json.success && json.code !== 200) {
      const err = new Error(json.message || '请求失败');
      err.response = json;
      err.status = resp.status;
      throw err;
    }
    return json;
  }

  if (!resp.ok) {
    throw new Error(`HTTP ${resp.status}`);
  }
  return resp;
}

async function apiJson(path, options = {}) {
  const result = await apiRequest(path, options);
  return result.data !== undefined ? result : result;
}

function showOutput(el, data, isError) {
  if (!el) return;
  el.className = 'output' + (isError ? ' error' : '');
  el.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
}

function showJsonDetails(el, data, isError) {
  if (!el) return;
  el.className = 'json-details-wrap' + (isError ? ' error' : '');
  const pre = document.createElement('pre');
  pre.className = 'output' + (isError ? ' error' : '');
  pre.textContent = typeof data === 'string' ? data : JSON.stringify(data, null, 2);
  el.innerHTML = '';
  const details = document.createElement('details');
  details.className = 'json-details';
  const summary = document.createElement('summary');
  summary.textContent = '原始 JSON（调试）';
  details.appendChild(summary);
  details.appendChild(pre);
  el.appendChild(details);
}

function formatStatusBadge(status) {
  const map = {
    WAITING: '待处理',
    SYSTEM_REJECT: '系统拒绝',
    MANUAL_REVIEW: '人工复核',
    FINAL_PASS: '已通过',
    FINAL_REJECT: '已拒绝',
    REQUIRED: '需补材料',
    SUBMITTED: '已提交材料',
    NONE: '无需材料',
  };
  return map[status] || status;
}

async function fetchBlobWithToken(path, token, queryParams) {
  const base = getApiBase();
  let url = path.startsWith('http') ? path : `${base}${path.startsWith('/') ? path : '/' + path}`;
  if (queryParams) {
    const qs = new URLSearchParams(queryParams).toString();
    if (qs) url += (url.includes('?') ? '&' : '?') + qs;
  }
  const resp = await fetch(url, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!resp.ok) {
    throw new Error(`请求失败 HTTP ${resp.status}`);
  }
  return resp.blob();
}

async function downloadWithToken(path, filename, token) {
  const blob = await fetchBlobWithToken(path, token);
  const a = document.createElement('a');
  const objectUrl = URL.createObjectURL(blob);
  a.href = objectUrl;
  a.download = filename || 'download';
  a.click();
  URL.revokeObjectURL(objectUrl);
}
