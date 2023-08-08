package com.toughenflat.chatai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;

import com.toughenflat.chatai.dto.UserLoginRequest;
import com.toughenflat.chatai.dto.UserRegisterRequest;
import com.toughenflat.chatai.session.LoginSession;
import com.toughenflat.chatai.utils.SequentialUuidHexGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(value = "user")
public class UserEntity {
    /**
     * id
     */
    @TableId(value = "id", type = IdType.INPUT)
    private String id;

    /**
     * 微信小程序: 用户openid
     */
    @TableField(value = "openid")
    private String openid;

    /**
     * 微信小程序: unionid
     */
    @TableField(value = "unionid")
    private String unionid;

    /**
     * 微信小程序: session_key
     */
    @TableField(value = "session_key")
    private String sessionKey;

    /**
     * 用户名
     */
    @TableField(value = "username")
    private String username;

    /**
     * 密码
     */
    @TableField(value = "password")
    private String password;

    /**
     * 昵称
     */
    @TableField(value = "nickname")
    private String nickname;

    /**
     * 手机号码
     */
    @TableField(value = "mobile")
    private String mobile;

    /**
     * 邮箱
     */
    @TableField(value = "email")
    private String email;

    /**
     * 头像
     */
    @TableField(value = "header")
    private String header;

    /**
     * 性别
     */
    @TableField(value = "gender")
    private Byte gender;

    /**
     * 启用状态: 0-正常, 1-锁定
     */
    @TableField(value = "status")
    private Byte status;

    /**
     * 注册时间
     */
    @TableField(value = "create_time")
    private Date createTime;

    /**
     * 社交用户在社交软件的ID
     */
    @TableField(value = "social_uid")
    private String socialUid;

    /**
     * 更新时间
     */
    @TableField(value = "update_time")
    private Date updateTime;

    /**
     * 用户等级: 0-管理员, 1-普通用户, 2-vip用户
     */
    @TableField(value = "level")
    private Integer level;

    public static UserEntity of(UserRegisterRequest req){
        UserEntity userEntity = new UserEntity();
        userEntity.setId(SequentialUuidHexGenerator.generate());
        userEntity.setUsername(req.getUserName());
        userEntity.setEmail(req.getEmail());
        userEntity.setCreateTime(new Date());
        userEntity.setUpdateTime(new Date());
        userEntity.setMobile(req.getPhone());
        userEntity.setNickname(req.getUserName());
        return userEntity;
    }
}