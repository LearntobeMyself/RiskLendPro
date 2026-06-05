let selectedApplyId = null;
let selectedBCardUserId = null;
const materialObjectUrls = [];

function adminToken() {
  return getToken('ADMIN');
}

function revokeMaterialObjectUrls() {
  while (materialObjectUrls.length) {
    URL.revokeObjectURL(materialObjectUrls.pop());
  }
}

function isImageMaterial(m) {
  const mime = (m.mimeType || '').toLowerCase();
  if (mime.startsWith('image/')) return true;
  const name = (m.originalName || '').toLowerCase();
  return /\.(jpg|jpeg|png|gif|webp)$/.test(name);
}

function isPdfMaterial(m) {
  const mime = (m.mimeType || '').toLowerCase();
  if (mime.includes('pdf')) return true;
  return (m.originalName || '').toLowerCase().endsWith('.pdf');
}

function updateTokenInfo() {
  const t = adminToken();
  document.getElementById('tokenInfo').textContent = t
    ? '已登录，Token: ' + t.slice(0, 40) + '...'
    : '未登录';
}

function renderRiskList(data) {
  const tbody = document.getElementById('riskBody');
  tbody.innerHTML = '';
  const list = (data && (data.records || data.list)) || [];
  if (!list.length) {
    tbody.innerHTML = '<tr><td colspan="6">暂无数据</td></tr>';
    return;
  }
  list.forEach((row) => {
    const tr = document.createElement('tr');
    tr.className = 'clickable';
    tr.innerHTML = `
      <td>${row.applyId || ''}</td>
      <td>${row.userName || ''}</td>
      <td>${row.totalScore ?? '-'}</td>
      <td>${sysDecisionLabel(row.sysDecision) || row.sysDecision || '-'}</td>
      <td>${row.status || ''}</td>
      <td>${row.applyTime || ''}</td>`;
    tr.onclick = () => {
      selectedApplyId = row.applyId;
      document.getElementById('approveApplyId').value = row.applyId || '';
      loadReport(row.applyId);
    };
    tbody.appendChild(tr);
  });
}

async function renderMaterials(materials, supplementStatus) {
  const box = document.getElementById('materialPreview');
  revokeMaterialObjectUrls();
  box.innerHTML = '';

  const heading = document.createElement('h3');
  heading.textContent = materials.length
    ? `补充材料（共 ${materials.length} 份）`
    : '补充材料';
  box.appendChild(heading);

  if (supplementStatus) {
    const statusP = document.createElement('p');
    statusP.className = 'hint';
    statusP.textContent = '材料状态: ' + formatStatusBadge(supplementStatus) + ' (' + supplementStatus + ')';
    box.appendChild(statusP);
  }

  if (!materials || !materials.length) {
    const empty = document.createElement('p');
    empty.className = 'hint';
    empty.textContent = '该申请暂无上传材料。请确认用户已在用户端上传（supplementStatus 为 REQUIRED 时可传，全部必填项完成后为 SUBMITTED）。';
    box.appendChild(empty);
    return;
  }

  const grid = document.createElement('div');
  grid.className = 'material-preview-grid';
  box.appendChild(grid);

  const token = adminToken();
  if (!token) {
    box.appendChild(document.createElement('p')).textContent = '请先登录管理员账号以预览材料';
    return;
  }

  for (const m of materials) {
    const card = document.createElement('div');
    card.className = 'material-card';

    const meta = document.createElement('div');
    meta.className = 'material-meta';
    meta.innerHTML = `
      <strong>${materialTypeLabel(m.materialType)}</strong> (${m.materialType || '-'})<br>
      ${m.originalName || '-'}<br>
      <span class="hint">${m.uploadTime || ''}</span>`;
    card.appendChild(meta);

    const actions = document.createElement('div');
    actions.className = 'material-actions';

    const downloadBtn = document.createElement('button');
    downloadBtn.type = 'button';
    downloadBtn.className = 'secondary';
    downloadBtn.textContent = '下载';
    downloadBtn.onclick = async () => {
      try {
        await downloadWithToken(
          `/admin/supplement/file/${m.materialId}`,
          m.originalName,
          token
        );
      } catch (e) {
        alert(e.message);
      }
    };
    actions.appendChild(downloadBtn);

    const previewWrap = document.createElement('div');
    previewWrap.className = 'material-thumb-wrap';

    try {
      const path = `/admin/supplement/file/${m.materialId}`;
      if (isImageMaterial(m)) {
        const blob = await fetchBlobWithToken(path, token, { inline: 'true' });
        const objectUrl = URL.createObjectURL(blob);
        materialObjectUrls.push(objectUrl);
        const img = document.createElement('img');
        img.className = 'material-thumb';
        img.src = objectUrl;
        img.alt = m.originalName || m.materialType;
        img.title = '点击放大';
        img.onclick = () => {
          const overlay = document.createElement('div');
          overlay.className = 'material-lightbox';
          const big = document.createElement('img');
          big.src = objectUrl;
          big.alt = m.originalName;
          overlay.onclick = () => overlay.remove();
          overlay.appendChild(big);
          document.body.appendChild(overlay);
        };
        previewWrap.appendChild(img);
      } else if (isPdfMaterial(m)) {
        const openBtn = document.createElement('button');
        openBtn.type = 'button';
        openBtn.textContent = '新标签打开 PDF';
        openBtn.onclick = async () => {
          try {
            const blob = await fetchBlobWithToken(path, token, { inline: 'true' });
            const objectUrl = URL.createObjectURL(blob);
            window.open(objectUrl, '_blank');
            setTimeout(() => URL.revokeObjectURL(objectUrl), 60000);
          } catch (e) {
            alert(e.message);
          }
        };
        previewWrap.appendChild(openBtn);
        const pdfHint = document.createElement('p');
        pdfHint.className = 'hint';
        pdfHint.textContent = 'PDF 文件';
        previewWrap.appendChild(pdfHint);
      } else {
        const hint = document.createElement('p');
        hint.className = 'hint';
        hint.textContent = '不支持预览，请下载查看';
        previewWrap.appendChild(hint);
      }
    } catch (e) {
      const err = document.createElement('p');
      err.className = 'hint';
      err.style.color = '#b91c1c';
      err.textContent = '预览失败: ' + e.message;
      previewWrap.appendChild(err);
    }

    card.appendChild(previewWrap);
    card.appendChild(actions);
    grid.appendChild(card);
  }
}

function resolveReportDecision(data) {
  return data.systemDecision || data.sysDecision || '-';
}

function parseAuditRemarkLines(auditRemark) {
  if (!auditRemark) return [];
  return String(auditRemark)
    .split(/[,;]\s*/)
    .map((s) => s.trim())
    .filter(Boolean);
}

function formatNum(n, digits) {
  if (n == null || n === '') return '-';
  const v = Number(n);
  if (Number.isNaN(v)) return String(n);
  return digits != null ? v.toFixed(digits) : String(Math.round(v * 100) / 100);
}

function renderScoreBar(score, thresholds) {
  const t = { ...SCORE_THRESHOLDS, ...(thresholds || {}) };
  const min = t.min;
  const max = t.max;
  const span = max - min;
  const s = Number(score);
  const pct = Number.isNaN(s) ? 0 : Math.min(100, Math.max(0, ((s - min) / span) * 100));
  const rejectPct = ((t.rejectBelow - min) / span) * 100;
  const approvePct = ((t.approveFrom - min) / span) * 100;

  return `
    <div class="score-bar-wrap">
      <div class="score-bar-labels">
        <span>${min}</span>
        <span class="score-threshold reject">拒绝 &lt;${t.rejectBelow}</span>
        <span class="score-threshold manual">人工 ${t.rejectBelow}–${t.approveFrom - 1}</span>
        <span class="score-threshold approve">通过 ≥${t.approveFrom}</span>
        <span>${max}</span>
      </div>
      <div class="score-bar">
        <div class="score-zone score-zone-reject" style="width:${rejectPct}%"></div>
        <div class="score-zone score-zone-manual" style="left:${rejectPct}%;width:${approvePct - rejectPct}%"></div>
        <div class="score-zone score-zone-approve" style="left:${approvePct}%;width:${100 - approvePct}%"></div>
        <div class="score-marker" style="left:${pct}%" title="${Number.isNaN(s) ? '' : Math.round(s) + ' 分'}"></div>
      </div>
    </div>`;
}

function renderScoreDetailsTable(details) {
  if (!details || !details.length) return '';
  const sorted = [...details].sort(
    (a, b) => Math.abs(Number(b.contribution) || 0) - Math.abs(Number(a.contribution) || 0)
  );
  let posSum = 0;
  let negSum = 0;
  const rows = sorted
    .map((row) => {
      const contrib = Number(row.contribution) || 0;
      if (contrib >= 0) posSum += contrib;
      else negSum += contrib;
      const negClass = contrib < 0 ? ' contrib-negative' : '';
      return `<tr class="${negClass.trim()}">
        <td>${row.feature || '-'}</td>
        <td>${row.description || '-'}</td>
        <td>${formatNum(row.value, 4)}</td>
        <td>${formatNum(row.weight, 4)}</td>
        <td><strong>${formatNum(row.contribution, 2)}</strong></td>
      </tr>`;
    })
    .join('');
  return `
    <table class="score-table">
      <thead>
        <tr>
          <th>特征</th><th>说明</th><th>取值</th><th>权重</th><th>贡献分</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
      <tfoot>
        <tr>
          <td colspan="4">正贡献合计</td>
          <td>${formatNum(posSum, 2)}</td>
        </tr>
        <tr>
          <td colspan="4">负贡献合计</td>
          <td class="contrib-negative">${formatNum(negSum, 2)}</td>
        </tr>
      </tfoot>
    </table>`;
}

const DISPLAY_GROUP_ORDER = ['基础', '收入', '征信', '负债', '风险', '行为', '资产'];

function renderScoreItemsAdminTable(scoreItems, scoreSummary) {
  if (!scoreItems || !scoreItems.length) return '';
  const visible = scoreItems.filter((row) => row.showInAdminTable !== false);
  if (!visible.length) return '<p class="hint">无显著评分因素可展示。</p>';

  const posText = scoreSummary?.positiveText
    ?? `合计加分 ${formatNum(scoreSummary?.positiveContribution ?? 0, 2)}`;
  const negText = scoreSummary?.negativeText
    ?? `合计减分 ${formatNum(Math.abs(scoreSummary?.negativeContribution ?? 0), 2)}`;

  const rows = visible
    .map((row) => {
      const contrib = Number(row.contribution) || 0;
      const negClass = contrib < 0 ? ' contrib-negative' : '';
      const impactCls = contrib >= 0 ? 'impact-positive' : 'impact-negative';
      const meaning = row.meaning
        ? `<span class="score-meaning">${row.meaning}</span>`
        : '-';
      return `<tr class="${negClass.trim()}">
        <td>${row.label || '-'}</td>
        <td>${row.displayValue ?? '-'}</td>
        <td>${meaning}</td>
        <td class="${impactCls}">${row.impactText || '-'}</td>
        <td><strong>${row.contributionText ?? formatNum(row.contribution, 2)}</strong></td>
      </tr>`;
    })
    .join('');

  return `
    <p class="hint score-detail-hint">下表说明各因素对客户信用的影响方向与幅度；「加减分」为模型贡献值，合计不等于最终信用分（含基础分与映射规则）。</p>
    <table class="score-table score-table-admin">
      <thead>
        <tr>
          <th>评分因素</th><th>客户情况</th><th>指标说明</th><th>影响</th><th>加减分</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
      <tfoot>
        <tr>
          <td colspan="4">${posText}</td>
          <td>${formatNum(scoreSummary?.positiveContribution ?? 0, 2)}</td>
        </tr>
        <tr>
          <td colspan="4">${negText}</td>
          <td class="contrib-negative">${formatNum(scoreSummary?.negativeContribution ?? 0, 2)}</td>
        </tr>
      </tfoot>
    </table>`;
}

function renderScoreItemsTechTable(scoreItems, scoreSummary) {
  if (!scoreItems || !scoreItems.length) return '';
  const posSum = scoreSummary?.positiveContribution ?? scoreItems.reduce(
    (s, row) => s + (Number(row.contribution) >= 0 ? Number(row.contribution) || 0 : 0),
    0
  );
  const negSum = scoreSummary?.negativeContribution ?? scoreItems.reduce(
    (s, row) => s + (Number(row.contribution) < 0 ? Number(row.contribution) || 0 : 0),
    0
  );
  const rows = scoreItems
    .map((row) => {
      const contrib = Number(row.contribution) || 0;
      const negClass = contrib < 0 ? ' contrib-negative' : '';
      const woeCell = row.woe != null ? formatNum(row.woe, 4) : '-';
      return `<tr class="${negClass.trim()}">
        <td>${row.label || '-'}</td>
        <td>${row.rawValueText ?? '-'}</td>
        <td>${woeCell}</td>
        <td>${formatNum(row.coefficient, 4)}</td>
        <td><strong>${formatNum(row.contribution, 2)}</strong></td>
      </tr>`;
    })
    .join('');
  return `
    <table class="score-table">
      <thead>
        <tr>
          <th>说明</th><th>原始值</th><th>WOE</th><th>系数</th><th>贡献分</th>
        </tr>
      </thead>
      <tbody>${rows}</tbody>
      <tfoot>
        <tr>
          <td colspan="4">正贡献合计</td>
          <td>${formatNum(posSum, 2)}</td>
        </tr>
        <tr>
          <td colspan="4">负贡献合计</td>
          <td class="contrib-negative">${formatNum(negSum, 2)}</td>
        </tr>
      </tfoot>
    </table>`;
}

function renderScoreItemsTable(scoreItems, scoreSummary) {
  if (!scoreItems || !scoreItems.length) return '';
  let html = renderScoreItemsAdminTable(scoreItems, scoreSummary);
  html += `
    <details class="score-tech-details">
      <summary>技术明细（WOE / 系数，供研发排查）</summary>
      ${renderScoreItemsTechTable(scoreItems, scoreSummary)}
    </details>`;
  return html;
}

function renderDisplayItemsTable(items, grouped) {
  if (!items || !items.length) return '';
  if (!grouped) {
    const rows = items
      .map((row) => {
        const hint = row.hint ? `<span class="hint"> (${row.hint})</span>` : '';
        return `<tr><td>${row.label || '-'}</td><td>${row.value ?? '-'}${hint}</td></tr>`;
      })
      .join('');
    return `<table class="score-table score-table-compact"><tbody>${rows}</tbody></table>`;
  }
  const byGroup = {};
  items.forEach((row) => {
    const g = row.group || '其他';
    if (!byGroup[g]) byGroup[g] = [];
    byGroup[g].push(row);
  });
  const orderedGroups = [
    ...DISPLAY_GROUP_ORDER.filter((g) => byGroup[g]),
    ...Object.keys(byGroup).filter((g) => !DISPLAY_GROUP_ORDER.includes(g)),
  ];
  let html = '';
  orderedGroups.forEach((group) => {
    html += `<h4 class="display-group-title">${group}</h4>`;
    html += renderDisplayItemsTable(byGroup[group], false);
  });
  return html;
}

function renderExternalFeaturesTable(ext) {
  if (!ext || typeof ext !== 'object') return '';
  const keys = Object.keys(ext);
  if (!keys.length) return '';
  const rows = keys
    .map((key) => {
      let val = ext[key];
      if (val === true) val = '是';
      if (val === false) val = '否';
      return `<tr><td>${externalFeatureLabel(key)}</td><td>${val ?? '-'}</td></tr>`;
    })
    .join('');
  return `<table class="score-table score-table-compact"><tbody>${rows}</tbody></table>`;
}

function renderUserDetailsTable(details) {
  if (!details || typeof details !== 'object') return '';
  const rows = Object.keys(details)
    .map((key) => {
      let val = details[key];
      if (val === true) val = '是';
      if (val === false) val = '否';
      return `<tr><td>${userDetailLabel(key)}</td><td>${val ?? '-'}</td></tr>`;
    })
    .join('');
  return `<table class="score-table score-table-compact"><tbody>${rows}</tbody></table>`;
}

function renderOutcomeBanner(data) {
  const finalStatus = data.finalStatus || '';
  const summary = data.outcomeSummary || '';
  const rejectGate = data.rejectGate;
  let cls = 'outcome-banner';
  let title = '';
  let body = summary;

  if (finalStatus === 'MANUAL_REVIEW') {
    cls += ' outcome-manual';
    title = '人工复核';
    if (!body) body = '该申请进入人工复核，请结合下方评分明细、规则原因与补充材料进行审批。';
  } else if (finalStatus === 'SYSTEM_REJECT') {
    cls += ' outcome-reject';
    title = rejectGateLabel(rejectGate) || '系统拒绝';
    if (!body) body = renderRejectExplanation(rejectGate, data);
  } else if (finalStatus === 'FINAL_PASS') {
    cls += ' outcome-pass';
    title = '已通过';
    body = summary || '评估已自动通过。';
  } else if (summary) {
    title = '处理结果';
  } else {
    return '';
  }

  return `<div class="${cls}"><strong>${title}</strong><p>${body}</p></div>`;
}

function renderRejectExplanation(rejectGate, data) {
  switch (rejectGate) {
    case 'BLACKLIST_L3':
      return '姓名、身份证地域与出生年份同时命中黑名单（三级），系统在算分前直接拒绝。';
    case 'INCOME_VERIFICATION':
      return (data.incomeVerification && data.incomeVerification.reason)
        ? `收入验真未通过：${data.incomeVerification.reason}`
        : '自填收入与第三方后台数据偏差超过 50%，收入验真硬拒绝。';
    case 'SCORE_LOW':
      return `信用分 ${data.totalScore != null ? Math.round(Number(data.totalScore)) : '-'} 低于拒绝线 ${SCORE_THRESHOLDS.rejectBelow}，评分卡自动拒绝。`;
    case 'RULE_AND_SCORE_LOW':
      return `虽命中规则闸（${data.ruleGate ? ruleTriggerLabel(data.ruleGate) : '规则'}），但信用分 ${data.totalScore != null ? Math.round(Number(data.totalScore)) : '-'} 低于拒绝线，分数闸优先拒绝。`;
    default:
      return '系统拒绝，请查看下方风控标签与 auditRemark。';
  }
}

function renderBlacklistSection(bl) {
  if (!bl || !bl.hit) return '';
  return `
    <section class="report-section report-section-alert">
      <h3>黑名单命中</h3>
      <table class="score-table score-table-compact">
        <tbody>
          <tr><td>是否命中</td><td><strong class="text-danger">是</strong></td></tr>
          <tr><td>级别</td><td>${bl.level || '-'}</td></tr>
          <tr><td>说明</td><td>${bl.reason || '-'}</td></tr>
          <tr><td>数据源</td><td>${bl.source || '-'}</td></tr>
        </tbody>
      </table>
    </section>`;
}

function renderIncomeSection(income) {
  if (!income) return '';
  const isHardReject = income.hardReject === true;
  const isManual = income.needManualReview === true;
  let statusText = income.passed ? '通过' : isHardReject ? '硬拒绝' : isManual ? '需人工复核' : '未通过';
  return `
    <section class="report-section ${isHardReject ? 'report-section-alert' : isManual ? 'report-section-warn' : ''}">
      <h3>收入验真</h3>
      <table class="score-table score-table-compact">
        <tbody>
          <tr><td>验真结果</td><td><strong>${statusText}</strong></td></tr>
          <tr><td>详情</td><td>${income.reason || '-'}</td></tr>
        </tbody>
      </table>
    </section>`;
}

function renderScoreRejectSection(data) {
  const gate = data.rejectGate;
  if (gate !== 'SCORE_LOW' && gate !== 'RULE_AND_SCORE_LOW') return '';
  const score = data.totalScore;
  if (score == null) return '';
  return `
    <section class="report-section report-section-alert">
      <h3>信用分拒绝详情</h3>
      <p>当前得分 <strong>${Math.round(Number(score))}</strong>，低于拒绝线 <strong>${SCORE_THRESHOLDS.rejectBelow}</strong>。</p>
      ${gate === 'RULE_AND_SCORE_LOW' ? `<p>同时命中规则闸：<strong>${ruleTriggerLabel(data.ruleGate)}</strong>，但因分数过低仍系统拒绝。</p>` : ''}
    </section>`;
}

function renderScoreDetailsMissingHint(data, score) {
  if (data.scoreRecomputed) {
    return 'Redis 报告未命中，已从申请数据只读重算评分明细（不影响评估终态）。';
  }
  if (data.scored === false || data.rejectGate === 'BLACKLIST_L3' || data.rejectGate === 'INCOME_VERIFICATION') {
    return '本路径在算分前或验真阶段拦截，无完整评分明细（设计如此）。';
  }
  if (data.reportCacheHit === false) {
    let msg = 'Redis 报告未命中（可能写入失败或未连接 Redis），';
    if (score != null) {
      msg += `当前仅 DB 兜底总分 ${Math.round(Number(score))}。`;
    } else {
      msg += '无评分明细。';
    }
    if (data.cacheWriteFailed) {
      msg += ' 提交时 Redis 写入已失败，请查后端 ERROR 日志。';
    }
    return msg;
  }
  if (data.reportCacheHit === true) {
    return '报告已缓存但评分明细为空，请查后端 scoreDetails 生成日志（非 TTL 过期）。';
  }
  return '评分明细不可用。';
}

function renderRiskReportDetail(data) {
  const box = document.getElementById('reportDetail');
  if (!box) return;
  if (!data || !Object.keys(data).length) {
    box.innerHTML = '';
    return;
  }

  const thresholds = data.scoreThresholds || SCORE_THRESHOLDS;
  const score = data.totalScore;
  const decision = resolveReportDecision(data);
  const finalStatus = data.finalStatus || '';
  const isManual = finalStatus === 'MANUAL_REVIEW';
  const isReject = finalStatus === 'SYSTEM_REJECT';
  const zoneHint = describeScoreZone(score, thresholds);
  const scoreZone = data.scoreZone || scoreZoneFromScore(score, thresholds);
  const riskTags = data.riskTags || [];
  const auditLines = parseAuditRemarkLines(data.auditRemark);
  const bl = data.blacklistCheck || {};
  const display = data.reportDisplay;
  const hasScoreItems = display?.scoreItems?.length > 0;
  const hasScoreDetails = !hasScoreItems && data.scoreDetails && data.scoreDetails.length > 0;

  let html = '';

  html += renderOutcomeBanner(data);
  html += renderBlacklistSection(bl);
  html += renderIncomeSection(data.incomeVerification);
  html += renderScoreRejectSection(data);

  html += '<section class="report-section">';
  html += `<h3>${isManual ? '人工复核 · 评分摘要' : isReject ? '拒绝案例 · 评分摘要' : '评分摘要'}</h3>`;
  html += '<div class="report-kv-grid">';
  html += `<div><span class="kv-label">信用总分</span><strong class="kv-score">${score != null ? Math.round(Number(score)) : '-'}</strong></div>`;
  html += `<div><span class="kv-label">模型决策</span>${sysDecisionLabel(decision)} (${decision})</div>`;
  html += `<div><span class="kv-label">分数区间</span>${scoreZone === 'MANUAL' ? '人工审核档' : scoreZone === 'APPROVE' ? '通过档' : scoreZone === 'REJECT' ? '拒绝档' : '-'}</div>`;
  html += `<div><span class="kv-label">建议额度</span>${data.suggestedAmount != null ? data.suggestedAmount : '-'}</div>`;
  html += `<div><span class="kv-label">终态</span>${formatStatusBadge(finalStatus || '-')} (${finalStatus || '-'})</div>`;
  html += `<div><span class="kv-label">规则闸</span>${data.ruleGate ? ruleTriggerLabel(data.ruleGate) : '-'}</div>`;
  html += `<div><span class="kv-label">拒绝闸</span>${data.rejectGate ? rejectGateLabel(data.rejectGate) : '-'}</div>`;
  html += `<div><span class="kv-label">补材料</span>${formatStatusBadge(data.supplementStatus || 'NONE')}</div>`;
  html += '</div>';
  if (zoneHint && score != null) {
    html += `<p class="score-zone-hint">${zoneHint}</p>`;
  }
  if (score != null && !Number.isNaN(Number(score))) {
    html += renderScoreBar(score, thresholds);
  }
  html += '</section>';

  if (isManual || riskTags.length || auditLines.length || data.ruleGate) {
    html += '<section class="report-section">';
    html += '<h3>风控原因 / 进人工依据</h3>';
    if (riskTags.length) {
      html += '<p>' + riskTags.map((t) => `<span class="badge tag">${t}</span>`).join(' ') + '</p>';
    } else if (isManual) {
      html += '<p class="hint">无额外规则标签，主要因信用分处于人工审核区间。</p>';
    }
    if (auditLines.length) {
      html += '<ul class="audit-remark-list">';
      auditLines.forEach((line) => {
        html += `<li>${line}</li>`;
      });
      html += '</ul>';
    }
    html += '</section>';
  }

  html += '<section class="report-section">';
  html += '<h3>评分明细</h3>';
  if (hasScoreItems) {
    html += renderScoreItemsTable(display.scoreItems, display.scoreSummary);
  } else if (hasScoreDetails) {
    html += renderScoreDetailsTable(data.scoreDetails);
  } else {
    html += '<p class="hint">' + renderScoreDetailsMissingHint(data, score);
    html += '</p>';
  }
  if (data.reportCacheHit != null) {
    html += `<p class="hint cache-meta">Redis 缓存: ${data.reportCacheHit ? '命中' : '未命中'}`;
    if (data.reportCachedAt) {
      html += ` | 写入时间: ${data.reportCachedAt}`;
    }
    if (data.scoreRecomputed) {
      html += ' | 明细已重算';
    }
    html += '</p>';
  }
  html += '</section>';

  if (display?.externalItems?.length) {
    html += '<section class="report-section">';
    html += '<h3>外部特征摘要</h3>';
    html += renderDisplayItemsTable(display.externalItems, true);
    html += '</section>';
  } else if (data.externalFeatures && Object.keys(data.externalFeatures).length) {
    html += '<section class="report-section">';
    html += '<h3>外部特征摘要</h3>';
    html += renderExternalFeaturesTable(data.externalFeatures);
    html += '</section>';
  }

  if (display?.userItems?.length) {
    html += '<section class="report-section">';
    html += '<h3>申请信息</h3>';
    html += renderDisplayItemsTable(display.userItems, false);
    html += '</section>';
  } else if (data.userDetails) {
    html += '<section class="report-section">';
    html += '<h3>申请信息</h3>';
    html += renderUserDetailsTable(data.userDetails);
    html += '</section>';
  }

  box.innerHTML = html;
}

async function loadReport(applyId) {
  if (!applyId) return;
  document.getElementById('reportApplyId').textContent = applyId;
  revokeMaterialObjectUrls();
  document.getElementById('materialPreview').innerHTML = '<p class="hint">加载材料预览中...</p>';
  const detailEl = document.getElementById('reportDetail');
  if (detailEl) detailEl.innerHTML = '<p class="hint">加载报告详情...</p>';
  try {
    const res = await apiRequest(`/admin/risk/report/${encodeURIComponent(applyId)}`, {
      token: adminToken(),
    });
    const data = res.data || {};
    const summaryEl = document.getElementById('reportSummary');
    if (summaryEl) {
      const score = data.totalScore != null ? Math.round(Number(data.totalScore)) : '-';
      const decision = resolveReportDecision(data);
      const ruleGate = data.ruleGate ? ruleTriggerLabel(data.ruleGate) : '-';
      const rejectGate = data.rejectGate ? rejectGateLabel(data.rejectGate) : '-';
      const sup = data.supplementStatus || '-';
      const outcome = data.outcomeSummary ? `<br><span class="hint">${data.outcomeSummary}</span>` : '';
      const cacheInfo = data.reportCacheHit != null
        ? `<br><span class="hint">Redis: ${data.reportCacheHit ? '命中' : '未命中'}${data.reportCachedAt ? ' · ' + data.reportCachedAt : ''}${data.scoreRecomputed ? ' · 明细已重算' : ''}</span>`
        : '';
      summaryEl.innerHTML = `
        <p><strong>信用分:</strong> ${score}
        | <strong>模型决策:</strong> ${sysDecisionLabel(decision)}
        | <strong>终态:</strong> ${formatStatusBadge(data.finalStatus || '-')}
        | <strong>规则闸:</strong> ${ruleGate}
        | <strong>拒绝闸:</strong> ${rejectGate}
        | <strong>补材料:</strong> ${formatStatusBadge(sup)} (${sup})${outcome}${cacheInfo}</p>`;
    }
    renderRiskReportDetail(data);
    showJsonDetails(document.getElementById('reportOut'), data);
    await renderMaterials(data.supplementMaterials, data.supplementStatus);
  } catch (e) {
    showJsonDetails(document.getElementById('reportOut'), e.message, true);
    if (detailEl) detailEl.innerHTML = '';
    document.getElementById('materialPreview').innerHTML = '';
  }
}

const BCARD_WATCH_LABELS = {
  NORMAL: '正常',
  DUE_SOON: '快到期',
  DUE_TODAY: '今日到期',
  OVERDUE: '已逾期',
};

function bCardWatchTag(level) {
  const label = BCARD_WATCH_LABELS[level] || level || '-';
  const cls = {
    NORMAL: 'bcard-tag-normal',
    DUE_SOON: 'bcard-tag-due-soon',
    DUE_TODAY: 'bcard-tag-due-today',
    OVERDUE: 'bcard-tag-overdue',
  }[level] || 'bcard-tag-normal';
  return `<span class="bcard-tag ${cls}">${label}</span>`;
}

function renderBCardList(list) {
  const tbody = document.getElementById('bCardBody');
  if (!tbody) return;
  tbody.innerHTML = '';
  if (!list || !list.length) {
    tbody.innerHTML = '<tr><td colspan="9">暂无 B 卡用户（需先完成放款）</td></tr>';
    return;
  }
  list.forEach((row) => {
    const tr = document.createElement('tr');
    tr.className = 'clickable';
    if (row.watchLevel === 'DUE_SOON' || row.watchLevel === 'DUE_TODAY') {
      tr.classList.add('bcard-row-due-soon');
    } else if (row.watchLevel === 'OVERDUE') {
      tr.classList.add('bcard-row-overdue');
    }
    const days = row.daysToDue != null ? row.daysToDue : '-';
    tr.innerHTML = `
      <td>${row.userName || ''}<br><span class="hint">${row.phone || ''}</span></td>
      <td>${row.bScore ?? '-'}</td>
      <td>${row.baseScore ?? '-'}</td>
      <td>${row.deltaScore ?? '-'}</td>
      <td>${days}</td>
      <td>${row.planStatus || '-'}</td>
      <td>${row.overdueLevel || '-'}</td>
      <td>${row.limitMultiplier != null ? row.limitMultiplier : '-'}</td>
      <td>${bCardWatchTag(row.watchLevel)}</td>`;
    tr.onclick = () => {
      selectedBCardUserId = row.userId;
      const hint = document.getElementById('bCardSelected');
      if (hint) {
        hint.textContent = `已选 userId=${row.userId}（${row.userName || ''}）`;
      }
    };
    tbody.appendChild(tr);
  });
}

async function loadBCardList() {
  try {
    const res = await apiRequest('/admin/b-card/monitor', { token: adminToken() });
    const list = Array.isArray(res.data) ? res.data : [];
    renderBCardList(list);
    showOutput(document.getElementById('bCardOut'), { count: list.length, data: list });
  } catch (e) {
    showOutput(document.getElementById('bCardOut'), e.message, true);
  }
}

async function recalculateBCard() {
  if (!selectedBCardUserId) {
    alert('请先在 B 卡列表中点击选择一行');
    return;
  }
  try {
    const res = await apiRequest(`/admin/b-card/recalculate/${selectedBCardUserId}`, {
      method: 'POST',
      token: adminToken(),
    });
    showOutput(document.getElementById('bCardOut'), res);
    loadBCardList();
  } catch (e) {
    showOutput(document.getElementById('bCardOut'), e.message, true);
  }
}

async function loadList() {
  const status = document.getElementById('listStatus')?.value ?? 'MANUAL_REVIEW';
  const qs = status
    ? `/admin/risk/list?status=${encodeURIComponent(status)}&page=1&size=20`
    : '/admin/risk/list?page=1&size=20';
  try {
    const res = await apiRequest(qs, {
      token: adminToken(),
    });
    renderRiskList(res.data);
  } catch (e) {
    alert(e.message);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('apiBase').value = getApiBase();
  updateTokenInfo();

  document.getElementById('btnSaveBase').onclick = () => {
    setApiBase(document.getElementById('apiBase').value.trim());
    alert('已保存: ' + getApiBase());
  };

  document.getElementById('btnRegister').onclick = async () => {
    const body = {
      username: document.getElementById('regUser').value,
      password: document.getElementById('regPwd').value,
      rePassword: document.getElementById('regRepwd').value,
      phoneNumber: document.getElementById('regPhone').value,
      email: document.getElementById('regEmail').value,
    };
    try {
      const res = await apiRequest('/admin/register', { method: 'POST', body });
      showOutput(document.getElementById('authOut'), res);
    } catch (e) {
      showOutput(document.getElementById('authOut'), e.message, true);
    }
  };

  document.getElementById('btnLogin').onclick = async () => {
    const body = {
      username: document.getElementById('loginUser').value,
      password: document.getElementById('loginPwd').value,
    };
    try {
      const res = await apiRequest('/admin/login', { method: 'POST', body });
      if (res.data && res.data.token) {
        setToken('ADMIN', res.data.token);
        updateTokenInfo();
      }
      showOutput(document.getElementById('authOut'), res);
    } catch (e) {
      showOutput(document.getElementById('authOut'), e.message, true);
    }
  };

  document.getElementById('btnLogout').onclick = () => {
    clearToken('ADMIN');
    updateTokenInfo();
  };

  document.getElementById('btnList').onclick = loadList;

  document.getElementById('btnBCardList').onclick = loadBCardList;
  document.getElementById('btnBCardRecalc').onclick = recalculateBCard;

  document.getElementById('btnApprove').onclick = async () => {
    const auditResult = document.getElementById('approveResult').value;
    const body = {
      applyId: document.getElementById('approveApplyId').value,
      auditResult,
      creditLimit: auditResult === 'PASS' ? parseFloat(document.getElementById('approveLimit').value) : 0,
      auditRemark: document.getElementById('approveRemark').value,
    };
    try {
      const res = await apiRequest('/admin/risk/approve', {
        method: 'POST',
        token: adminToken(),
        body,
      });
      showOutput(document.getElementById('approveOut'), res);
      loadList();
    } catch (e) {
      showOutput(document.getElementById('approveOut'), e.message, true);
    }
  };
});
