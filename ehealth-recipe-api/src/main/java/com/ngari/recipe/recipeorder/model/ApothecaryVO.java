package com.ngari.recipe.recipeorder.model;

import ctd.schema.annotation.ItemProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 药师信息用于前端展示
 *
 * @author fuzi
 */
@Getter
@Setter
public class ApothecaryVO implements Serializable {

    private static final long serialVersionUID = 2398885985048336367L;
    @ItemProperty(alias = "订单ID")
    private Integer recipeId;

    @ItemProperty(alias = "审核药师姓名")
    private String checkApothecaryName;

    @ItemProperty(alias = "审核药师身份证")
    private String checkApothecaryIdCard;

    @ItemProperty(alias = "发药药师姓名")
    private String dispensingApothecaryName;

    @ItemProperty(alias = "发药药师身份证")
    private String dispensingApothecaryIdCard;
}
