package com.toughenflat.chatai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "wx_system_key")
public class WxSystemKeyEntity {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /**
     * 小程序ID
     */
    @TableField(value = "app_id")
    private String appId;

    /**
     * 小程序密钥
     */
    @TableField(value = "app_secret")
    private String appSecret;
}
