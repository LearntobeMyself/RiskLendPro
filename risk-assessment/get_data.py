import pandas as pd
import requests
import io

# 确认过的宽表下载链接
url = "https://huggingface.co/datasets/jamirc/home_credit_default_risk/resolve/main/home_credit_train_ready.csv"

print("正在暴力截取前 500 条数据，这种方式最快...")

try:
    # 1. 使用 stream=True 流式请求，不直接下载整个文件
    with requests.get(url, stream=True, timeout=10) as r:
        r.raise_for_status()
        
        lines = []
        count = 0
        # 2. 逐行读取网络流
        for line in r.iter_lines():
            if count <= 500: # 1行表头 + 500行数据
                if line:
                    lines.append(line.decode('utf-8'))
                count += 1
            else:
                break # 够了，直接断开连接！

    # 3. 将截取的文本转换成 pandas 能懂的表格
    data_str = "\n".join(lines)
    df = pd.read_csv(io.StringIO(data_str))
    
    print(f"提取成功！当前表格包含: {df.shape[0]} 行, {df.shape[1]} 列")

    # 4. 保存到本地
    output_file = "home_credit_quick_sample.csv"
    df.to_csv(output_file, index=False)
    print(f"数据已存入: {output_file}")

except Exception as e:
    print(f"抓取失败: {e}")