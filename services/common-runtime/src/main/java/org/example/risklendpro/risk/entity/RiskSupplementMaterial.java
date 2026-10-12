package org.example.risklendpro.risk.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("risk_supplement_material")
public class RiskSupplementMaterial {
    @TableId(type = IdType.AUTO)
    private Long id;
    @TableField("apply_id")
    private String applyId;
    @TableField("user_id")
    private Long userId;
    @TableField("material_type")
    private String materialType;
    @TableField("original_name")
    private String originalName;
    @TableField("stored_path")
    private String storedPath;
    @TableField("file_size")
    private Long fileSize;
    @TableField("mime_type")
    private String mimeType;
    private String remark;
    @TableField("upload_time")
    private Date uploadTime;
    @TableField("expire_at")
    private Date expireAt;
    @TableField("verification_ocr")
    private String verificationOcr;
    @TableField("verification_ai_score")
    private Double verificationAiScore;
    @TableField("verification_tamper_score")
    private Double verificationTamperScore;
    @TableField("verification_status")
    private String verificationStatus;
    @TableField("verification_time")
    private Date verificationTime;
}
