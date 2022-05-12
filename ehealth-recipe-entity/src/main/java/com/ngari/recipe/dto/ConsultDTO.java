package com.ngari.recipe.dto;

import ctd.schema.annotation.ItemProperty;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @author zgy
 * @date 2022/1/11 18:32
 */
@Data
public class ConsultDTO implements Serializable {
    private static final long serialVersionUID = 5550549018567401105L;

    @ItemProperty(alias = "门诊单序号")
    private Integer consultId;
    @ItemProperty(alias = "钥匙圈渠道id")
    private String projectChannel;
    @ItemProperty(alias = "门诊方式")
    private Integer requestMode;
    @ItemProperty(alias = "门诊挂号序号（医保")
    private String registerNo;
    @ItemProperty(alias = "就诊人卡号")
    private String cardId;
    @ItemProperty(alias = "就诊人卡类型")
    private String cardType;
    @ItemProperty(alias = "门诊医生科室")
    private Integer consultDepart;
    @ItemProperty(alias = "门诊医生科室名称")
    private String consultDepartText;
    @ItemProperty(alias = "请求时间")
    private Date requestTime;
    @ItemProperty(alias = "病情描述")
    private String leaveMess;
    @ItemProperty(alias = "开始时间")
    private Date startDate;

    @ItemProperty(alias = "是否医保 0自费 1医保")
    private Integer medicalFlag;
    @ItemProperty(alias = "大病类型")
    private String illnessType;
    @ItemProperty(alias = "大病类型名称")
    private String illnessName;
}
