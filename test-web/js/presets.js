const PRESETS = {
  wuxiaowei: {
    register: {
      realName: '吴小微',
      phoneNumber: '13800138001',
      email: 'wu_xiaowei@example.com',
      idCard: '110112197705151001',
      password: 'Test123456',
      repassword: 'Test123456',
    },
    login: {
      phoneNumber: '13800138001',
      password: 'Test123456',
    },
    assessment: {
      idCard: '110112197705151001',
      name: '吴小微',
      phone: '13800138001',
      email: 'wu_xiaowei@example.com',
      gender: 1,
      birthday: '1977-05-15',
      education: '本科',
      marriage: '已婚',
      jobType: '企事业单位',
      monthlyIncome: '15000以上',
      hasHouse: true,
      hasCar: true,
      contactPhone: '13800138000',
    },
  },
  fangxiaochao: {
    register: {
      realName: '方小巢',
      phoneNumber: '13800138002',
      email: 'fang_xiaochao@example.com',
      idCard: '310000198402260267',
      password: 'Test123456',
      repassword: 'Test123456',
    },
    login: {
      phoneNumber: '13800138002',
      password: 'Test123456',
    },
    assessment: {
      idCard: '310000198402260267',
      name: '方小巢',
      phone: '13800138002',
      email: 'fang_xiaochao@example.com',
      gender: 0,
      birthday: '1984-02-26',
      education: '大专',
      marriage: '未婚',
      jobType: '自由职业',
      monthlyIncome: '8000-15000',
      hasHouse: false,
      hasCar: true,
      contactPhone: '13800138000',
    },
  },
  zhangxiaoliang: {
    register: {
      realName: '张小梁',
      phoneNumber: '13800138003',
      email: 'zhang_xiaoliang@example.com',
      idCard: '110112198212010000',
      password: 'Test123456',
      repassword: 'Test123456',
    },
    login: {
      phoneNumber: '13800138003',
      password: 'Test123456',
    },
    assessment: {
      idCard: '110112198212010000',
      name: '张小梁',
      phone: '13800138003',
      email: 'zhang_xiaoliang@example.com',
      gender: 0,
      birthday: '1982-12-01',
      education: '高中及以下',
      marriage: '已婚',
      jobType: '企事业单位',
      monthlyIncome: '8000-15000',
      hasHouse: true,
      hasCar: true,
      contactPhone: '13800138000',
    },
  },
  lisi: {
    register: {
      realName: '李四',
      phoneNumber: '13800138004',
      email: 'lisi@example.com',
      idCard: '110112200101080001',
      password: 'Test123456',
      repassword: 'Test123456',
    },
    login: {
      phoneNumber: '13800138004',
      password: 'Test123456',
    },
    assessment: {
      idCard: '110112200101080001',
      name: '李四',
      phone: '13800138004',
      email: 'lisi@example.com',
      gender: 1,
      birthday: '2001-01-08',
      education: '高中及以下',
      marriage: '未婚',
      jobType: '体力劳动者',
      monthlyIncome: '3000以下',
      hasHouse: false,
      hasCar: false,
      contactPhone: '13800138000',
    },
  },
  zhaoliu: {
    register: {
      realName: '赵六',
      phoneNumber: '13800138006',
      email: 'zhaoliu@example.com',
      idCard: '110112199602030012',
      password: 'Test123456',
      repassword: 'Test123456',
    },
    login: {
      phoneNumber: '13800138006',
      password: 'Test123456',
    },
    assessment: {
      idCard: '110112199602030012',
      name: '赵六',
      phone: '13800138006',
      email: 'zhaoliu@example.com',
      gender: 0,
      birthday: '1996-02-03',
      education: '本科',
      marriage: '已婚',
      jobType: '企事业单位',
      monthlyIncome: '15000以上',
      hasHouse: true,
      hasCar: false,
      contactPhone: '13800138000',
    },
  },
  zhouba: {
    register: {
      realName: '周八',
      phoneNumber: '13800138007',
      email: 'zhouba@example.com',
      idCard: '110112198011120027',
      password: 'Test123456',
      repassword: 'Test123456',
    },
    login: {
      phoneNumber: '13800138007',
      password: 'Test123456',
    },
    assessment: {
      idCard: '110112198011120027',
      name: '周八',
      phone: '13800138007',
      email: 'zhouba@example.com',
      gender: 0,
      birthday: '1980-11-12',
      education: '高中及以下',
      marriage: '已婚',
      jobType: '体力劳动者',
      monthlyIncome: '15000以上',
      hasHouse: false,
      hasCar: false,
      contactPhone: '13800138000',
    },
  },
  wujiu: {
    register: {
      realName: '吴九',
      phoneNumber: '13800138008',
      email: 'wujiu@example.com',
      idCard: '110112198809220017',
      password: 'Test123456',
      repassword: 'Test123456',
    },
    login: {
      phoneNumber: '13800138008',
      password: 'Test123456',
    },
    assessment: {
      idCard: '110112198809220017',
      name: '吴九',
      phone: '13800138008',
      email: 'wujiu@example.com',
      gender: 1,
      birthday: '1988-09-22',
      education: '高中及以下',
      marriage: '已婚',
      jobType: '体力劳动者',
      monthlyIncome: '15000以上',
      hasHouse: false,
      hasCar: true,
      contactPhone: '13800138000',
    },
  },
  sunqi: {
    register: {
      realName: '孙七',
      phoneNumber: '13800138009',
      email: 'sunqi@example.com',
      idCard: '110112198702150007',
      password: 'Test123456',
      repassword: 'Test123456',
    },
    login: {
      phoneNumber: '13800138009',
      password: 'Test123456',
    },
    assessment: {
      idCard: '110112198702150007',
      name: '孙七',
      phone: '13800138009',
      email: 'sunqi@example.com',
      gender: 0,
      birthday: '1987-02-15',
      education: '本科',
      marriage: '已婚',
      jobType: '企事业单位',
      monthlyIncome: '15000以上',
      hasHouse: true,
      hasCar: true,
      contactPhone: '13800138000',
    },
  },
};

const PRESET_META = {
  wujiu: {
    label: '吴九',
    expect: 'MANUAL_REVIEW · 收入异常 · 仅 INCOME_PROOF',
    badge: '需要上传证明材料',
  },
  fangxiaochao: {
    label: '方小巢',
    expect: 'SYSTEM_REJECT · L2+低分 · rejectGate=RULE_AND_SCORE_LOW',
    reusable: false,
    badge: '需清库',
  },
  zhangxiaoliang: {
    label: '张小梁',
    expect: 'MANUAL_REVIEW · 分数≈768 · ruleTrigger=SCORE_ONLY · 可选补材料',
  },
  sunqi: {
    label: '孙七',
    expect: 'MANUAL_REVIEW · L2黑名单 · 模型分>788(APPROVE) · 规则强制补材料',
    reusable: false,
    badge: '需清库',
  },
  lisi: {
    label: '李四',
    expect: 'SYSTEM_REJECT · 分数≈550 · rejectGate=SCORE_LOW',
  },
  zhaoliu: {
    label: '赵六',
    expect: 'FINAL_PASS · 分数≈950 · 自动通过',
  },
  wuxiaowei: {
    label: '吴小微',
    expect: 'SYSTEM_REJECT · L3黑名单 · 不算分',
  },
  zhouba: {
    label: '周八',
    expect: 'SYSTEM_REJECT · 收入虚报>50% · 不算分',
  },
};

const PRESET_GROUPS = [
  {
    title: '规则闸',
    presets: ['wujiu', 'fangxiaochao', 'sunqi'],
  },
  {
    title: '分数闸',
    presets: ['zhangxiaoliang', 'lisi', 'zhaoliu'],
  },
  {
    title: '黑名单 / 收入硬拒',
    presets: ['wuxiaowei', 'zhouba'],
  },
];

const PRESET_LABELS = Object.fromEntries(
  Object.entries(PRESET_META).map(([key, meta]) => [key, meta.label])
);

const FANGXIAOCHAO_RESET_HINT =
  '重测方小巢需先清库（见 test-web/README.md）：\n' +
  'DELETE FROM risk_supplement_material WHERE apply_id IN ' +
  '(SELECT apply_id FROM risk_assessment WHERE user_id = ' +
  '(SELECT id FROM user WHERE phone_number = \'13800138002\'));\n' +
  'DELETE FROM risk_assessment WHERE user_id = ' +
  '(SELECT id FROM user WHERE phone_number = \'13800138002\');\n' +
  'DELETE FROM user WHERE phone_number = \'13800138002\';';

const SUNQI_RESET_HINT =
  '重测孙七需先清库并确保 L2 黑名单种子已入库（孙*,110112,1986，见 sql/seed_blacklist_sunqi_l2.sql）：\n' +
  'DELETE FROM risk_supplement_material WHERE apply_id IN ' +
  '(SELECT apply_id FROM risk_assessment WHERE user_id = ' +
  '(SELECT id FROM user WHERE phone_number = \'13800138009\'));\n' +
  'DELETE FROM risk_assessment WHERE user_id = ' +
  '(SELECT id FROM user WHERE phone_number = \'13800138009\');\n' +
  'DELETE FROM user WHERE phone_number = \'13800138009\';';

function loadPreset(name) {
  const preset = PRESETS[name];
  if (!preset) {
    return Promise.reject(new Error('未知预设: ' + name));
  }
  return Promise.resolve(preset);
}
