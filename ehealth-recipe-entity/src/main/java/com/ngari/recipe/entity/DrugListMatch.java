package com.ngari.recipe.entity;

import ctd.schema.annotation.Dictionary;
import ctd.schema.annotation.ItemProperty;
import ctd.schema.annotation.Schema;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

import static javax.persistence.GenerationType.IDENTITY;

/**
 * created by shiyuping on 2019/2/1
 */
@Entity
@Schema
@Table(name = "base_druglist_matching")
@Access(AccessType.PROPERTY)
public class DrugListMatch implements java.io.Serializable {
    public static final long serialVersionUID = -3983203173007645688L;

    @ItemProperty(alias = "药品序号")
    private Integer drugId;

    @ItemProperty(alias = "his药品编码")
    private String organDrugCode;

    @Column(name = "organDrugCode", length = 100)
    public String getOrganDrugCode() {
        return organDrugCode;
    }

    public void setOrganDrugCode(String organDrugCode) {
        this.organDrugCode = organDrugCode;
    }

    @ItemProperty(alias = "药品名称")
    private String drugName;

    @ItemProperty(alias = "商品名")
    private String saleName;

    @ItemProperty(alias = "药品规格")
    private String drugSpec;

    @ItemProperty(alias = "药品包装数量")
    private Integer pack;

    @ItemProperty(alias = "药品单位")
    private String unit;

    @ItemProperty(alias = "药品类型")
    @Dictionary(id = "eh.base.dictionary.DrugType")
    private Integer drugType;

    @ItemProperty(alias = "一次剂量")
    private Double useDose;

    @ItemProperty(alias = "剂量单位")
    private String useDoseUnit;

    @ItemProperty(alias = "生产厂家")
    private String producer;

    @ItemProperty(alias = "参考价格")
    private BigDecimal price;

    @ItemProperty(alias = "状态")//0未匹配 1已匹配 2已提交
    private Integer status;

    @ItemProperty(alias = "适用症状")
    private String indications;

    @ItemProperty(alias = "拼音码")
    private String pyCode;

    @ItemProperty(alias = "创建时间")
    private Date createDt;

    @ItemProperty(alias = "最后修改时间")
    private Date lastModify;

    @ItemProperty(alias = "批准文号")
    private String approvalNumber;

    @ItemProperty(alias = "国药准字")
    private String licenseNumber;

    @ItemProperty(alias = "药品本位码")
    private String standardCode;

    @Column(name = "licenseNumber", length = 30)
    public String getLicenseNumber() {
        return licenseNumber;
    }

    public void setLicenseNumber(String licenseNumber) {
        this.licenseNumber = licenseNumber;
    }

    @Column(name = "standardCode", length = 30)
    public String getStandardCode() {
        return standardCode;
    }

    public void setStandardCode(String standardCode) {
        this.standardCode = standardCode;
    }

    @ItemProperty(alias = "匹配的药品id")
    private Integer matchDrugId;

    @ItemProperty(alias = "是否是新增药品")
    private Integer isNew;

    @ItemProperty(alias = "来源机构")
    private Integer sourceOrgan;

    @ItemProperty(alias = "剂型")
    private String drugForm;

    @ItemProperty(alias = "包装材料")
    private String packingMaterials;

    @ItemProperty(alias = "是否基药")
    private Integer baseDrug;

    @ItemProperty(alias = "对照人")
    private String operator;

    @ItemProperty(alias = "用药频率")
    private String usingRate;

    @ItemProperty(alias = "用药途径")
    private String usePathways;

    @ItemProperty(alias = "默认一次剂量")
    private Double defaultUseDose;

    @ItemProperty(alias = "院内搜索关键字")
    private String retrievalCode;

    @Column(name = "retrievalCode ")
    public String getRetrievalCode() {
        return retrievalCode;
    }

    public void setRetrievalCode(String retrievalCode) {
        this.retrievalCode = retrievalCode;
    }

    @Column(name = "usingRate", length = 10)
    public String getUsingRate() {
        return usingRate;
    }

    public void setUsingRate(String usingRate) {
        this.usingRate = usingRate;
    }

    @Column(name = "usePathways", length = 10)
    public String getUsePathways() {
        return usePathways;
    }

    public void setUsePathways(String usePathways) {
        this.usePathways = usePathways;
    }

    @Column(name = "defaultUseDose", precision = 10, scale = 3)
    public Double getDefaultUseDose() {
        return defaultUseDose;
    }

    public void setDefaultUseDose(Double defaultUseDose) {
        this.defaultUseDose = defaultUseDose;
    }

    @Column(name = "operator", length = 20)
    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    @Column(name = "drugForm", length = 20)
    public String getDrugForm() {
        return drugForm;
    }

    public void setDrugForm(String drugForm) {
        this.drugForm = drugForm;
    }

    @Column(name = "packingMaterials", length = 50)
    public String getPackingMaterials() {
        return packingMaterials;
    }

    public void setPackingMaterials(String packingMaterials) {
        this.packingMaterials = packingMaterials;
    }

    @Column(name = "baseDrug", length = 1)
    public Integer getBaseDrug() {
        return baseDrug;
    }

    public void setBaseDrug(Integer baseDrug) {
        this.baseDrug = baseDrug;
    }

    @Column(name = "matchDrugId", length = 11)
    public Integer getMatchDrugId() {
        return matchDrugId;
    }

    public void setMatchDrugId(Integer matchDrugId) {
        this.matchDrugId = matchDrugId;
    }

    @Column(name = "isNew", length = 1)
    public Integer getIsNew() {
        return isNew;
    }

    public void setIsNew(Integer isNew) {
        this.isNew = isNew;
    }

    @Column(name = "sourceOrgan", length = 11)
    public Integer getSourceOrgan() {
        return sourceOrgan;
    }

    public void setSourceOrgan(Integer sourceOrgan) {
        this.sourceOrgan = sourceOrgan;
    }

    public DrugListMatch() {
    }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "drugId", unique = true, nullable = false)
    public Integer getDrugId() {
        return this.drugId;
    }

    public void setDrugId(Integer drugId) {
        this.drugId = drugId;
    }

    @Column(name = "drugName", length = 50)
    public String getDrugName() {
        return this.drugName;
    }

    public void setDrugName(String drugName) {
        this.drugName = drugName;
    }

    @Column(name = "saleName", length = 50)
    public String getSaleName() {
        return this.saleName;
    }

    public void setSaleName(String saleName) {
        this.saleName = saleName;
    }

    @Column(name = "drugSpec", length = 50)
    public String getDrugSpec() {
        return this.drugSpec;
    }

    public void setDrugSpec(String drugSpec) {
        this.drugSpec = drugSpec;
    }

    @Column(name = "unit", length = 6)
    public String getUnit() {
        return this.unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    @Column(name = "drugType")
    public Integer getDrugType() {
        return this.drugType;
    }

    public void setDrugType(Integer drugType) {
        this.drugType = drugType;
    }

    @Column(name = "useDose", precision = 10, scale = 3)
    public Double getUseDose() {
        return this.useDose;
    }

    public void setUseDose(Double useDose) {
        this.useDose = useDose;
    }

    @Column(name = "useDoseUnit", length = 6)
    public String getUseDoseUnit() {
        return this.useDoseUnit;
    }

    public void setUseDoseUnit(String useDoseUnit) {
        this.useDoseUnit = useDoseUnit;
    }


    @Column(name = "producer", length = 20)
    public String getProducer() {
        return this.producer;
    }

    public void setProducer(String producer) {
        this.producer = producer;
    }

    @Column(name = "price", precision = 10)
    public BigDecimal getPrice() {
        return this.price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    @Column(name = "status")
    public Integer getStatus() {
        return this.status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @Column(name = "indications", length = 50)
    public String getIndications() {
        return this.indications;
    }

    public void setIndications(String indications) {
        this.indications = indications;
    }

    @Column(name = "pyCode", length = 20)
    public String getPyCode() {
        return this.pyCode;
    }

    public void setPyCode(String pyCode) {
        this.pyCode = pyCode;
    }

    @Column(name = "createDt", length = 19)
    public Date getCreateDt() {
        return this.createDt;
    }

    public void setCreateDt(Date createDt) {
        this.createDt = createDt;
    }

    @Column(name = "lastModify", length = 19)
    public Date getLastModify() {
        return this.lastModify;
    }

    public void setLastModify(Date lastModify) {
        this.lastModify = lastModify;
    }

    @Column(name = "pack", nullable = false, length = 11)
    public Integer getPack() {
        return pack;
    }

    public void setPack(Integer pack) {
        this.pack = pack;
    }

    @Column(name = "approvalNumber", length = 100)
    public String getApprovalNumber() {
        return approvalNumber;
    }

    public void setApprovalNumber(String approvalNumber) {
        this.approvalNumber = approvalNumber;
    }


}
