package com.ngari.recipe.recipe.model;

import lombok.Data;

import java.io.Serializable;

/**
 * @author yinsheng
 * @date 2020\12\3 0003 13:49
 */
@Data
public class GiveModeButtonBean implements Serializable{

    private static final long serialVersionUID = -5939365788332205225L;
    //按钮key
    private String showButtonKey;
    //展示前端按钮名称
    private String showButtonName;
    //对接方式  1 标准 2 门诊缴费 3 跳转到第三方
    private String buttonSkipType;


}
