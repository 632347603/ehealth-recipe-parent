package com.ngari.recipe.recipe.model;

import ctd.schema.annotation.ItemProperty;
import ctd.schema.annotation.Schema;

import java.io.Serializable;
import java.util.Date;

/**
 * 处方扩展信息
 */
@Schema
public class RecipeExtendBean implements Serializable {

    private static final long serialVersionUID = 2528413275115207345L;

    @ItemProperty(alias = "处方ID")
    private Integer recipeId;

    @ItemProperty(alias = "挂号序号")
    private String registerID;

    @ItemProperty(alias = "主诉")
    private String mainDieaseDescribe;

    @ItemProperty(alias = "现病史")
    private String currentMedical;

    @ItemProperty(alias = "既往史")
    private String histroyMedical;

    @ItemProperty(alias = "过敏史")
    private String allergyMedical;

    @ItemProperty(alias = "发病日期")
    private Date onsetDate;

    /**以下为互联网医院字段*/
    @ItemProperty(alias = "现病史")
    private String historyOfPresentIllness;

    @ItemProperty(alias = "处理方法")
    private String handleMethod;

    @ItemProperty(alias = "体格检查")
    private String physicalCheck;
    /**为互联网医院字段*/

    @ItemProperty(alias = "HIS处方关联的卡类型")
    private String  cardTypeName;

    @ItemProperty(alias = "HIS处方关联的卡号")
    private String  cardNo;

    @ItemProperty(alias = "患者类型 自费 0 商保 1 普通医保 2 慢病医保 3 省医保33 杭州市医保3301 衢州市医保3308 巨化医保3308A")
    private String patientType;

    @ItemProperty(alias = "his返回的配送药企代码")
    private String deliveryCode;

    @ItemProperty(alias = "his返回的配送药企名称")
    private String deliveryName;

    @ItemProperty(alias = "医保返回的医院机构编码")
    private String hospOrgCodeFromMedical;

    @ItemProperty(alias = "参保地统筹区")
    private String insuredArea;

    @ItemProperty(alias = "医保结算请求串")
    private String medicalSettleData;

    @ItemProperty(alias = "门诊挂号序号（医保）")
    private String registerNo;

    @ItemProperty(alias = "HIS收据号（医保）")
    private String hisSettlementNo;

    @ItemProperty(alias = "处方预结算返回支付总金额")
    private String preSettleTotalAmount;

    @ItemProperty(alias = "处方预结算返回医保支付金额")
    private String fundAmount;

    @ItemProperty(alias = "处方预结算返回自费金额")
    private String cashAmount;

    @ItemProperty(alias = "慢病病种标识")
    private String chronicDiseaseFlag;
    @ItemProperty(alias = "慢病病种代码")
    private String chronicDiseaseCode;
    @ItemProperty(alias = "慢病病种名称")
    private String chronicDiseaseName;
    @ItemProperty(alias = "用药医嘱")
    private String drugEntrustment;
    //用户页面选择
    @ItemProperty(alias = "是否长处方")
    private String isLongRecipe;

    //开当前处方的配置项信息
    @ItemProperty(alias = "可开长处方按钮状态、长处方开药天数、非长处方开药天数")
    private String recipeJsonConfig;

    /**
     * 患者医保类型（编码）
     */
    private String medicalType;

    /**
     * 患者医保类型（名称）
     */
    private String medicalTypeText;

    public RecipeExtendBean() {
    }

    public RecipeExtendBean(Integer recipeId, String historyOfPresentIllness, String mainDieaseDescribe,
                            String handleMethod, String physicalCheck) {
        this.recipeId = recipeId;
        this.historyOfPresentIllness = historyOfPresentIllness;
        this.mainDieaseDescribe = mainDieaseDescribe;
        this.handleMethod = handleMethod;
        this.physicalCheck = physicalCheck;
    }

    public Integer getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(Integer recipeId) {
        this.recipeId = recipeId;
    }

    public String getMainDieaseDescribe() {
        return mainDieaseDescribe;
    }

    public void setMainDieaseDescribe(String mainDieaseDescribe) {
        this.mainDieaseDescribe = mainDieaseDescribe;
    }

    public String getCurrentMedical() {
        return currentMedical;
    }

    public void setCurrentMedical(String currentMedical) {
        this.currentMedical = currentMedical;
    }

    public String getHistroyMedical() {
        return histroyMedical;
    }

    public void setHistroyMedical(String histroyMedical) {
        this.histroyMedical = histroyMedical;
    }

    public String getAllergyMedical() {
        return allergyMedical;
    }

    public void setAllergyMedical(String allergyMedical) {
        this.allergyMedical = allergyMedical;
    }

    public Date getOnsetDate() {
        return onsetDate;
    }

    public void setOnsetDate(Date onsetDate) {
        this.onsetDate = onsetDate;
    }

    public String getHistoryOfPresentIllness() {
        return historyOfPresentIllness;
    }

    public void setHistoryOfPresentIllness(String historyOfPresentIllness) {
        this.historyOfPresentIllness = historyOfPresentIllness;
    }

    public String getHandleMethod() {
        return handleMethod;
    }

    public void setHandleMethod(String handleMethod) {
        this.handleMethod = handleMethod;
    }

    public String getPhysicalCheck() {
        return physicalCheck;
    }

    public void setPhysicalCheck(String physicalCheck) {
        this.physicalCheck = physicalCheck;
    }

    public String getCardTypeName() {
        return cardTypeName;
    }

    public void setCardTypeName(String cardTypeName) {
        this.cardTypeName = cardTypeName;
    }

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getRegisterID() {
        return registerID;
    }

    public void setRegisterID(String registerID) {
        this.registerID = registerID;
    }

    public String getDeliveryCode() {
        return deliveryCode;
    }

    public void setDeliveryCode(String deliveryCode) {
        this.deliveryCode = deliveryCode;
    }

    public String getDeliveryName() {
        return deliveryName;
    }

    public void setDeliveryName(String deliveryName) {
        this.deliveryName = deliveryName;
    }

    public String getHospOrgCodeFromMedical() {
        return hospOrgCodeFromMedical;
    }

    public void setHospOrgCodeFromMedical(String hospOrgCodeFromMedical) {
        this.hospOrgCodeFromMedical = hospOrgCodeFromMedical;
    }

    public String getInsuredArea() {
        return insuredArea;
    }

    public void setInsuredArea(String insuredArea) {
        this.insuredArea = insuredArea;
    }

    public String getMedicalSettleData() {
        return medicalSettleData;
    }

    public void setMedicalSettleData(String medicalSettleData) {
        this.medicalSettleData = medicalSettleData;
    }

    public String getRegisterNo() {
        return registerNo;
    }

    public void setRegisterNo(String registerNo) {
        this.registerNo = registerNo;
    }

    public String getHisSettlementNo() {
        return hisSettlementNo;
    }

    public void setHisSettlementNo(String hisSettlementNo) {
        this.hisSettlementNo = hisSettlementNo;
    }

    public String getPreSettleTotalAmount() {
        return preSettleTotalAmount;
    }

    public void setPreSettleTotalAmount(String preSettleTotalAmount) {
        this.preSettleTotalAmount = preSettleTotalAmount;
    }

    public String getFundAmount() {
        return fundAmount;
    }

    public void setFundAmount(String fundAmount) {
        this.fundAmount = fundAmount;
    }

    public String getCashAmount() {
        return cashAmount;
    }

    public void setCashAmount(String cashAmount) {
        this.cashAmount = cashAmount;
    }

    public String getChronicDiseaseFlag() {
        return chronicDiseaseFlag;
    }

    public void setChronicDiseaseFlag(String chronicDiseaseFlag) {
        this.chronicDiseaseFlag = chronicDiseaseFlag;
    }

    public String getChronicDiseaseCode() {
        return chronicDiseaseCode;
    }

    public void setChronicDiseaseCode(String chronicDiseaseCode) {
        this.chronicDiseaseCode = chronicDiseaseCode;
    }

    public String getChronicDiseaseName() {
        return chronicDiseaseName;
    }

    public void setChronicDiseaseName(String chronicDiseaseName) {
        this.chronicDiseaseName = chronicDiseaseName;
    }

    public String getPatientType() {
        return patientType;
    }

    public void setPatientType(String patientType) {
        this.patientType = patientType;
    }

    public String getMedicalType() {
        return medicalType;
    }

    public void setMedicalType(String medicalType) {
        this.medicalType = medicalType;
    }

    public String getMedicalTypeText() {
        return medicalTypeText;
    }

    public void setMedicalTypeText(String medicalTypeText) {
        this.medicalTypeText = medicalTypeText;
    }

    public String getDrugEntrustment() {
        return drugEntrustment;
    }

    public void setDrugEntrustment(String drugEntrustment) {
        this.drugEntrustment = drugEntrustment;
    }

    public String getIsLongRecipe() {
        return isLongRecipe;
    }

    public void setIsLongRecipe(String isLongRecipe) {
        this.isLongRecipe = isLongRecipe;
    }

    public String getRecipeJsonConfig() {
        return recipeJsonConfig;
    }

    public void setRecipeJsonConfig(String recipeJsonConfig) {
        this.recipeJsonConfig = recipeJsonConfig;
    }
}
