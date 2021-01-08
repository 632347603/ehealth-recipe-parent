package com.ngari.recipe.entity;

import ctd.schema.annotation.Dictionary;
import ctd.schema.annotation.ItemProperty;
import ctd.schema.annotation.Schema;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

import static javax.persistence.GenerationType.IDENTITY;

/**
 * Created by  on 2017/5/22.
 * @author jiangtingfeng
 */
@Schema
@Entity
@Table(name = "cdr_commonRecipe")
@Access(AccessType.PROPERTY)
public class CommonRecipe implements Serializable{

    private static final long serialVersionUID = 1500970890296225446L;

    @ItemProperty(alias="医生身份ID")
    private Integer doctorId;

    @ItemProperty(alias="常用方名称")
    private String commonRecipeName;

    @ItemProperty(alias="常用方Id")
    private Integer commonRecipeId;

    @ItemProperty(alias="处方类型")
    @Dictionary(id = "eh.cdr.dictionary.RecipeType")
    private Integer recipeType;

    @ItemProperty(alias="创建时间")
    private Date createDt;

    @ItemProperty(alias="最后修改时间")
    private Date lastModify;

    @ItemProperty(alias="机构代码")
    private Integer organId;

    @ItemProperty(alias = "药房id主键")
    private Integer pharmacyId;
    @ItemProperty(alias = "药房名称")
    private String pharmacyName;

    @ItemProperty(alias = "常用方中的药品信息")
    private String recipeDetailJsonConfig;

    @ItemProperty(alias = "是否是长处方")
    private String isLongRecipe;

    @Column(name = "recipeDetailJsonConfig")
    public String getRecipeDetailJsonConfig() {
        return recipeDetailJsonConfig;
    }

    public void setRecipeDetailJsonConfig(String recipeDetailJsonConfig) {
        this.recipeDetailJsonConfig = recipeDetailJsonConfig;
    }

    @Column(name = "isLongRecipe")
    public String getIsLongRecipe() {
        return isLongRecipe;
    }

    public void setIsLongRecipe(String isLongRecipe) {
        this.isLongRecipe = isLongRecipe;
    }

    @Column(name = "OrganId")
    public Integer getOrganId() {
        return organId;
    }

    public void setOrganId(Integer organId) {
        this.organId = organId;
    }

    //开当前处方的配置项信息
    @ItemProperty(alias = "可开长处方按钮状态、长处方开药天数、非长处方开药天数")
    private String recipeJsonConfig;

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "CommonRecipeId", nullable = false)
    public Integer getCommonRecipeId() {
        return commonRecipeId;
    }

    public void setCommonRecipeId(Integer commonRecipeId) {
        this.commonRecipeId = commonRecipeId;
    }

    @Column(name = "RecipeType", nullable = false)
    public Integer getRecipeType() {
        return recipeType;
    }

    public void setRecipeType(Integer recipeType) {
        this.recipeType = recipeType;
    }

    @Column(name = "DoctorId", nullable = false)
    public Integer getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(Integer doctorId) {
        this.doctorId = doctorId;
    }

    @Column(name = "CommonRecipeName", nullable = false)
    public String getCommonRecipeName() {
        return commonRecipeName;
    }

    public void setCommonRecipeName(String commonRecipeName) {
        this.commonRecipeName = commonRecipeName;
    }

    @Column(name = "CreateDt", length = 19)
    public Date getCreateDt() {
        return createDt;
    }

    public void setCreateDt(Date createDt) {
        this.createDt = createDt;
    }

    @Column(name = "LastModify", length = 19)
    public Date getLastModify() {
        return lastModify;
    }

    public void setLastModify(Date lastModify) {
        this.lastModify = lastModify;
    }

    public String getRecipeJsonConfig() {
        return recipeJsonConfig;
    }

    public void setRecipeJsonConfig(String recipeJsonConfig) {
        this.recipeJsonConfig = recipeJsonConfig;
    }

    @Column(name = "pharmacyId")
    public Integer getPharmacyId() {
        return pharmacyId;
    }

    public void setPharmacyId(Integer pharmacyId) {
        this.pharmacyId = pharmacyId;
    }

    @Column(name = "pharmacyName")
    public String getPharmacyName() {
        return pharmacyName;
    }

    public void setPharmacyName(String pharmacyName) {
        this.pharmacyName = pharmacyName;
    }

    @Override
    public String toString() {
        return "CommonRecipe{" +
                "doctorId=" + doctorId +
                ", commonRecipeName='" + commonRecipeName + '\'' +
                ", commonRecipeId=" + commonRecipeId +
                ", recipeType=" + recipeType +
                ", createDt=" + createDt +
                ", lastModify=" + lastModify +
                ", organId=" + organId +
                ", pharmacyId=" + pharmacyId +
                ", pharmacyName='" + pharmacyName + '\'' +
                ", recipeJsonConfig='" + recipeJsonConfig + '\'' +
                '}';
    }
}
