let pollTimer = null;
let lastRequirementsData = null;
let batchUploading = false;
let lastSubmitEligibility = null;

function userToken() {
  return getToken('USER');
}

function fillRegister(data) {
  document.getElementById('regName').value = data.realName || '';
  document.getElementById('regPhone').value = data.phoneNumber || '';
  document.getElementById('regEmail').value = data.email || '';
  document.getElementById('regIdCard').value = data.idCard || '';
  document.getElementById('regPwd').value = data.password || '';
  document.getElementById('regRepwd').value = data.repassword || '';
}

function fillLogin(data) {
  document.getElementById('loginPhone').value = data.phoneNumber || '';
  document.getElementById('loginPwd').value = data.password || '';
}

function fillAssessment(data) {
  document.getElementById('aIdCard').value = data.idCard || '';
  document.getElementById('aName').value = data.name || '';
  document.getElementById('aPhone').value = data.phone || '';
  document.getElementById('aEmail').value = data.email || '';
  document.getElementById('aGender').value = data.gender ?? 0;
  document.getElementById('aBirthday').value = data.birthday || '';
  document.getElementById('aEducation').value = data.education || '';
  document.getElementById('aMarriage').value = data.marriage || '';
  document.getElementById('aJob').value = data.jobType || '';
  document.getElementById('aIncome').value = data.monthlyIncome || '';
  document.getElementById('aHouse').value = String(data.hasHouse ?? false);
  document.getElementById('aCar').value = String(data.hasCar ?? false);
  document.getElementById('aContact').value = data.contactPhone || '';
}

async function applyPreset(name) {
  try {
    const preset = await loadPreset(name);
    fillRegister(preset.register);
    fillLogin(preset.login);
    fillAssessment(preset.assessment);
    const meta = (typeof PRESET_META !== 'undefined' && PRESET_META[name]) || {};
    const label = meta.label || name;
    let msg = '已填充「' + label + '」测试数据（注册 / 登录 / 评估表单）';
    if (meta.expect) {
      msg += '\n\n预期：' + meta.expect;
    }
    if (meta.reusable === false && typeof FANGXIAOCHAO_RESET_HINT !== 'undefined' && name === 'fangxiaochao') {
      msg += '\n\n' + FANGXIAOCHAO_RESET_HINT;
    }
    if (meta.reusable === false && typeof SUNQI_RESET_HINT !== 'undefined' && name === 'sunqi') {
      msg += '\n\n' + SUNQI_RESET_HINT;
    }
    alert(msg);
  } catch (e) {
    alert(e.message);
  }
}

function initPresetButtons() {
  const container = document.getElementById('presetButtons');
  if (!container || typeof PRESET_GROUPS === 'undefined') return;
  container.innerHTML = '';
  PRESET_GROUPS.forEach((group) => {
    const section = document.createElement('div');
    section.className = 'preset-group';
    const title = document.createElement('div');
    title.className = 'preset-group-title';
    title.textContent = group.title;
    section.appendChild(title);
    const row = document.createElement('div');
    row.className = 'preset-group-btns';
    group.presets.forEach((key) => {
      const meta = PRESET_META[key] || { label: key };
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.className = 'secondary preset-btn';
      btn.dataset.preset = key;
      let text = meta.label;
      if (meta.badge) {
        text += ' (' + meta.badge + ')';
      }
      btn.textContent = text;
      if (meta.expect) {
        btn.title = meta.expect;
      }
      btn.onclick = () => applyPreset(key);
      row.appendChild(btn);
    });
    section.appendChild(row);
    container.appendChild(section);
  });
}

function updateTokenInfo() {
  const t = userToken();
  document.getElementById('tokenInfo').textContent = t
    ? '已登录，Token: ' + t.slice(0, 40) + '...'
    : '未登录';
  if (!t) {
    lastSubmitEligibility = null;
    renderSubmitEligibility(null);
  }
}

function renderSubmitEligibility(data) {
  const el = document.getElementById('applyEligibility');
  const btn = document.getElementById('btnSubmit');
  if (!el) return;
  if (!data || data.canSubmit !== false) {
    el.className = 'eligibility-banner hidden';
    el.innerHTML = '';
    if (btn) btn.disabled = false;
    return;
  }
  el.className = 'eligibility-banner eligibility-warn';
  let html = '<strong>暂不可重复提交评估</strong><p>' + (data.reason || '请稍后再试') + '</p>';
  if (data.existingApplyId) {
    html += '<p class="hint">已有申请：' + data.existingApplyId;
    if (data.existingStatus) {
      html += '（' + data.existingStatus + '）';
    }
    html += '</p>';
  }
  if (data.retryAfterDays != null) {
    html += '<p class="hint">约 ' + data.retryAfterDays + ' 天后可再次申请</p>';
  }
  el.innerHTML = html;
  if (btn) btn.disabled = true;
}

async function loadSubmitEligibility() {
  if (!userToken()) {
    renderSubmitEligibility(null);
    return null;
  }
  try {
    const res = await apiRequest('/risk/assessment/submit-eligibility', { token: userToken() });
    lastSubmitEligibility = res.data || null;
    renderSubmitEligibility(lastSubmitEligibility);
    return lastSubmitEligibility;
  } catch (e) {
    renderSubmitEligibility(null);
    return null;
  }
}

function renderStatusSummary(data) {
  const el = document.getElementById('statusSummary');
  if (!data) {
    el.innerHTML = '';
    return;
  }
  const st = data.status || '';
  const sup = data.supplementStatus || 'NONE';
  const score = data.totalScore != null ? data.totalScore : '-';
  const sysDec = data.sysDecision || '-';
  const rule = data.ruleTrigger ? ruleTriggerLabel(data.ruleTrigger) : '-';
  const uploaded = (data.uploadedMaterialTypes || []).map(materialTypeLabel).join('、') || '-';
  el.innerHTML = `
    <p><strong>applyId:</strong> ${data.applyId || '-'}
    | <strong>状态:</strong> ${formatStatusBadge(st)} (${st})
    | <strong>终态:</strong> ${data.isFinal ? '是' : '否'}
    | <strong>信用分:</strong> ${score}
    | <strong>模型决策:</strong> ${sysDec}
    | <strong>规则触发:</strong> ${rule}
    | <strong>补材料:</strong> ${formatStatusBadge(sup)} (${sup})
    | <strong>已传类型:</strong> ${uploaded}</p>`;
}

function getUploadedTypeSet(requirements) {
  const set = new Set(requirements.uploadedMaterialTypes || []);
  (requirements.uploadedMaterials || []).forEach((m) => {
    if (m.materialType) set.add(m.materialType);
  });
  return set;
}

function setUploadProgress(text) {
  const el = document.getElementById('uploadProgress');
  if (el) el.textContent = text || '';
}

function setBatchButtonsDisabled(disabled) {
  const same = document.getElementById('btnUploadSame');
  const all = document.getElementById('btnUploadAll');
  if (same) same.disabled = disabled;
  if (all) all.disabled = disabled;
}

function getRequiredMaterialCodes(requirements) {
  if (!requirements || !requirements.requiredMaterials) return [];
  const uploaded = getUploadedTypeSet(requirements);
  return requirements.requiredMaterials
    .filter((r) => r.required && !uploaded.has(r.code))
    .map((r) => r.code);
}

function renderUploadArea(requirements) {
  const area = document.getElementById('uploadArea');
  lastRequirementsData = requirements;
  if (!requirements || !requirements.requiredMaterials || requirements.requiredMaterials.length === 0) {
    area.innerHTML = '<p class="hint">暂无需要上传的材料</p>';
    return;
  }
  const uploadedSet = getUploadedTypeSet(requirements);
  area.innerHTML = '<p><strong>按清单上传（jpg/png/pdf，≤5MB）</strong></p>';
  requirements.requiredMaterials.forEach((req) => {
    const already = uploadedSet.has(req.code);
    const row = document.createElement('div');
    row.className = 'material-row';
    row.dataset.type = req.code;
    row.innerHTML = `
      <label>${req.label}${req.required ? ' *' : ''} (${req.code})${already ? ' <span class="hint">[已上传]</span>' : ''}</label>
      <input type="file" accept=".jpg,.jpeg,.png,.pdf" data-type="${req.code}" ${already ? 'disabled' : ''}>
      <button type="button" class="upload-btn" ${already ? 'disabled' : ''}>${already ? '已上传' : '上传'}</button>`;
    if (!already) {
      row.querySelector('.upload-btn').addEventListener('click', () => uploadOne(req.code, row.querySelector('input[type=file]')));
    }
    area.appendChild(row);
  });
  if (requirements.uploadedMaterials && requirements.uploadedMaterials.length) {
    const ul = document.createElement('ul');
    requirements.uploadedMaterials.forEach((m) => {
      const li = document.createElement('li');
      li.textContent = `${materialTypeLabel(m.materialType)} - ${m.originalName} (${m.uploadTime || ''})`;
      ul.appendChild(li);
    });
    area.appendChild(document.createElement('p')).textContent = '已上传:';
    area.appendChild(ul);
  }
}

async function uploadFile(materialType, file) {
  const fd = new FormData();
  fd.append('materialType', materialType);
  fd.append('file', file);
  return apiRequest('/risk/assessment/supplement/upload', {
    method: 'POST',
    token: userToken(),
    body: fd,
  });
}

async function uploadOne(materialType, fileInput) {
  const file = fileInput.files[0];
  if (!file) {
    alert('请选择文件');
    return;
  }
  try {
    const res = await uploadFile(materialType, file);
    showOutput(document.getElementById('supplementOut'), res);
    await loadRequirements();
    await loadStatus();
  } catch (e) {
    showOutput(document.getElementById('supplementOut'), e.message, true);
    throw e;
  }
}

async function uploadAllWithSameFile() {
  if (batchUploading) return;
  if (!lastRequirementsData) {
    alert('请先点击「拉取材料清单」');
    return;
  }
  const codes = getRequiredMaterialCodes(lastRequirementsData);
  if (!codes.length) {
    alert('没有必填材料项');
    return;
  }
  const input = document.createElement('input');
  input.type = 'file';
  input.accept = '.jpg,.jpeg,.png,.pdf';
  input.onchange = async () => {
    const file = input.files[0];
    if (!file) return;
    batchUploading = true;
    setBatchButtonsDisabled(true);
    try {
      for (let i = 0; i < codes.length; i++) {
        const code = codes[i];
        setUploadProgress(`上传中 ${i + 1}/${codes.length}: ${materialTypeLabel(code)}...`);
        const res = await uploadFile(code, file);
        showOutput(document.getElementById('supplementOut'), res);
      }
      setUploadProgress('全部必填项已上传');
      await loadRequirements();
      await loadStatus();
      alert('一键上传完成');
    } catch (e) {
      alert('上传失败: ' + e.message);
      setUploadProgress('');
    } finally {
      batchUploading = false;
      setBatchButtonsDisabled(false);
    }
  };
  input.click();
}

async function uploadAllSelected() {
  if (batchUploading) return;
  const rows = document.querySelectorAll('#uploadArea .material-row');
  const tasks = [];
  const uploadedSet = lastRequirementsData ? getUploadedTypeSet(lastRequirementsData) : new Set();
  rows.forEach((row) => {
    const input = row.querySelector('input[type=file]');
    const code = row.dataset.type || input.dataset.type;
    if (uploadedSet.has(code)) return;
    if (input && input.files[0]) {
      tasks.push({ code, file: input.files[0] });
    }
  });
  if (!tasks.length) {
    alert('请至少在各行中选择文件');
    return;
  }
  batchUploading = true;
  setBatchButtonsDisabled(true);
  try {
    for (let i = 0; i < tasks.length; i++) {
      const { code, file } = tasks[i];
      setUploadProgress(`上传中 ${i + 1}/${tasks.length}: ${materialTypeLabel(code)}...`);
      const res = await uploadFile(code, file);
      showOutput(document.getElementById('supplementOut'), res);
    }
    setUploadProgress('所选文件已全部上传');
    await loadRequirements();
    await loadStatus();
    alert('批量上传完成');
  } catch (e) {
    alert('上传失败: ' + e.message);
    setUploadProgress('');
  } finally {
    batchUploading = false;
    setBatchButtonsDisabled(false);
  }
}

async function loadStatus() {
  try {
    const res = await apiRequest('/risk/assessment/status', { token: userToken() });
    showOutput(document.getElementById('statusOut'), res);
    renderStatusSummary(res.data);
    await loadSubmitEligibility();
  } catch (e) {
    showOutput(document.getElementById('statusOut'), e.message, true);
  }
}

async function loadRequirements() {
  try {
    const res = await apiRequest('/risk/assessment/supplement/requirements', { token: userToken() });
    showOutput(document.getElementById('supplementOut'), res);
    renderUploadArea(res.data);
  } catch (e) {
    showOutput(document.getElementById('supplementOut'), e.message, true);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  initPresetButtons();
  document.getElementById('apiBase').value = getApiBase();
  updateTokenInfo();

  document.getElementById('btnSaveBase').onclick = () => {
    setApiBase(document.getElementById('apiBase').value.trim());
    alert('已保存: ' + getApiBase());
  };

  document.getElementById('btnRegister').onclick = async () => {
    const body = {
      realName: document.getElementById('regName').value,
      phoneNumber: document.getElementById('regPhone').value,
      email: document.getElementById('regEmail').value,
      idCard: document.getElementById('regIdCard').value,
      password: document.getElementById('regPwd').value,
      repassword: document.getElementById('regRepwd').value,
    };
    try {
      const res = await apiRequest('/auth/register', { method: 'POST', body });
      showOutput(document.getElementById('authOut'), res);
    } catch (e) {
      showOutput(document.getElementById('authOut'), e.message, true);
    }
  };

  document.getElementById('btnLogin').onclick = async () => {
    const body = {
      phoneNumber: document.getElementById('loginPhone').value,
      password: document.getElementById('loginPwd').value,
    };
    try {
      const res = await apiRequest('/auth/login', { method: 'POST', body });
      if (res.data && res.data.token) {
        setToken('USER', res.data.token);
        updateTokenInfo();
        await loadSubmitEligibility();
      }
      showOutput(document.getElementById('authOut'), res);
    } catch (e) {
      showOutput(document.getElementById('authOut'), e.message, true);
    }
  };

  document.getElementById('btnLogout').onclick = () => {
    clearToken('USER');
    updateTokenInfo();
  };

  document.getElementById('btnSubmit').onclick = async () => {
    if (lastSubmitEligibility && lastSubmitEligibility.canSubmit === false) {
      renderSubmitEligibility(lastSubmitEligibility);
      showOutput(document.getElementById('submitOut'), lastSubmitEligibility.reason, true);
      return;
    }
    const body = {
      idCard: document.getElementById('aIdCard').value,
      name: document.getElementById('aName').value,
      phone: document.getElementById('aPhone').value,
      email: document.getElementById('aEmail').value,
      gender: parseInt(document.getElementById('aGender').value, 10),
      birthday: document.getElementById('aBirthday').value,
      education: document.getElementById('aEducation').value,
      marriage: document.getElementById('aMarriage').value,
      jobType: document.getElementById('aJob').value,
      monthlyIncome: document.getElementById('aIncome').value,
      hasHouse: document.getElementById('aHouse').value === 'true',
      hasCar: document.getElementById('aCar').value === 'true',
      contactPhone: document.getElementById('aContact').value,
    };
    try {
      const res = await apiRequest('/risk/assessment/submit', {
        method: 'POST',
        token: userToken(),
        body,
      });
      showOutput(document.getElementById('submitOut'), res);
      await loadSubmitEligibility();
      await loadStatus();
    } catch (e) {
      showOutput(document.getElementById('submitOut'), e.message, true);
      if (/重复|处理中|已通过|再次申请/.test(e.message || '')) {
        await loadSubmitEligibility();
      }
    }
  };

  document.getElementById('btnStatus').onclick = loadStatus;

  document.getElementById('btnPoll').onclick = () => {
    if (pollTimer) clearInterval(pollTimer);
    loadStatus();
    pollTimer = setInterval(loadStatus, 3000);
  };

  document.getElementById('btnStopPoll').onclick = () => {
    if (pollTimer) clearInterval(pollTimer);
    pollTimer = null;
  };

  document.getElementById('btnResult').onclick = async () => {
    try {
      const res = await apiRequest('/risk/assessment/result', { token: userToken() });
      showOutput(document.getElementById('statusOut'), res);
    } catch (e) {
      showOutput(document.getElementById('statusOut'), e.message, true);
    }
  };

  document.getElementById('btnRequirements').onclick = loadRequirements;
  document.getElementById('btnUploadSame').onclick = uploadAllWithSameFile;
  document.getElementById('btnUploadAll').onclick = uploadAllSelected;
});
