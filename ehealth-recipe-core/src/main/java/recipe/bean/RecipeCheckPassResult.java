package recipe.bean;


import com.ngari.recipe.entity.Recipedetail;
import ctd.schema.annotation.ItemProperty;

import java.math.BigDecimal;
import java.util.List;

/**
 * 审核通过HIS返回数据对象
 * company: ngarihealth
 *
 * @author: 0184/yu_yun
 * date:2016/6/6.
 */
public class RecipeCheckPassResult {
    /**
     * BASE平台处方ID
     */
    private Integer recipeId;

    /**
     * HIS平台处方ID
     */
    private String recipeCode;

    /**
     * 患者ID
     */
    private String patientID;

    /**
     * 病人挂号序号
     */
    private String registerID;

    /**
     * 处方总金额
     */
    private BigDecimal totalMoney;

    /**
     * 处方详情数据
     */
    private List<Recipedetail> detailList;

    /**
     * 患者医保类型（编码）
     */
    private String medicalType;

    /**
     * 患者医保类型（名称）
     */
    private String medicalTypeText;

    /**
     * his处方付费序号合集
     */
    private String recipeCostNumber;

    /**
     * 取药窗口
     */
    private String pharmNo;

    /**
     * 诊断序号
     */
    private String hisDiseaseSerial;

    /**
     * 病历号
     */
    private String medicalRecordNumber;


    @ItemProperty(
            alias = "中药处方辩证论证费支付状态 0:待支付1:已支付"
    )
    private Integer visitPayFlag;
    @ItemProperty(
            alias = "中药处方辩证论证费"
    )
    private BigDecimal visitMoney;

    @ItemProperty(
            alias = "中药处方辩证论证费代码"
    )
    private String visitCode;

    public String getVisitCode() {
        return visitCode;
    }

    public void setVisitCode(String visitCode) {
        this.visitCode = visitCode;
    }

    public Integer getVisitPayFlag() {
        return visitPayFlag;
    }

    public void setVisitPayFlag(Integer visitPayFlag) {
        this.visitPayFlag = visitPayFlag;
    }

    public BigDecimal getVisitMoney() {
        return visitMoney;
    }

    public void setVisitMoney(BigDecimal visitMoney) {
        this.visitMoney = visitMoney;
    }

    public String getHisDiseaseSerial() {
        return hisDiseaseSerial;
    }

    public void setHisDiseaseSerial(String hisDiseaseSerial) {
        this.hisDiseaseSerial = hisDiseaseSerial;
    }

    public String getPharmNo() {
        return pharmNo;
    }

    public void setPharmNo(String pharmNo) {
        this.pharmNo = pharmNo;
    }


    public String getRecipeCostNumber() {
        return recipeCostNumber;
    }

    public void setRecipeCostNumber(String recipeCostNumber) {
        this.recipeCostNumber = recipeCostNumber;
    }

    public Integer getRecipeId() {
        return recipeId;
    }

    public void setRecipeId(Integer recipeId) {
        this.recipeId = recipeId;
    }

    public String getRecipeCode() {
        return recipeCode;
    }

    public void setRecipeCode(String recipeCode) {
        this.recipeCode = recipeCode;
    }

    public String getPatientID() {
        return patientID;
    }

    public void setPatientID(String patientID) {
        this.patientID = patientID;
    }

    public BigDecimal getTotalMoney() {
        return totalMoney;
    }

    public void setTotalMoney(BigDecimal totalMoney) {
        this.totalMoney = totalMoney;
    }

    public List<Recipedetail> getDetailList() {
        return detailList;
    }

    public void setDetailList(List<Recipedetail> detailList) {
        this.detailList = detailList;
    }

    public String getRegisterID() {
        return registerID;
    }

    public void setRegisterID(String registerID) {
        this.registerID = registerID;
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

    public String getMedicalRecordNumber() {
        return medicalRecordNumber;
    }

    public void setMedicalRecordNumber(String medicalRecordNumber) {
        this.medicalRecordNumber = medicalRecordNumber;
    }
}
