package com.ngari.recipe.entity;

import ctd.account.session.ClientSession;
import ctd.schema.annotation.Dictionary;
import ctd.schema.annotation.ItemProperty;
import ctd.schema.annotation.Schema;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

import static javax.persistence.GenerationType.IDENTITY;

/**
 * @author yuyun
 */
@Entity
@Schema
@Table(name = "cdr_recipe")
@Access(AccessType.PROPERTY)
public class Recipe implements Serializable {

    private static final long serialVersionUID = -6170665419368031590L;

    @ItemProperty(alias = "处方序号")
    private Integer recipeId;

    @ItemProperty(alias = "订单编号")
    private String orderCode;

    @ItemProperty(alias = "就诊序号")
    private Integer clinicId;

    @ItemProperty(alias = "主索引")
    private String mpiid;

    @ItemProperty(alias = "患者医院病历号")
    private String patientID;

    @ItemProperty(alias = "患者状态 1正常  9注销")
    private int patientStatus;

    @ItemProperty(alias = "开方机构")
    @Dictionary(id = "eh.base.dictionary.Organ")
    private Integer clinicOrgan;

    @ItemProperty(alias = "开方机构名称")
    private String organName;

    @ItemProperty(alias = "处方来源机构")
    @Dictionary(id = "eh.base.dictionary.Organ")
    private Integer originClinicOrgan;

    @ItemProperty(alias = "处方号码")
    private String recipeCode;

    @ItemProperty(alias = "处方来源源处方号")
    private String originRecipeCode;

    @ItemProperty(alias = "处方类型")
    @Dictionary(id = "eh.cdr.dictionary.RecipeType")
    private Integer recipeType;

    @ItemProperty(alias = "开方科室")
    @Dictionary(id = "eh.base.dictionary.Depart")
    private Integer depart;

    @ItemProperty(alias = "开方医生")
    @Dictionary(id = "eh.base.dictionary.Doctor")
    private Integer doctor;

    @ItemProperty(alias = "开方时间")
    private Date createDate;

    @ItemProperty(alias = "剂数")
    private Integer copyNum;

    @ItemProperty(alias = "处方金额")
    private BigDecimal totalMoney;

    @ItemProperty(alias = "机构疾病名称")
    private String organDiseaseName;

    @ItemProperty(alias = "机构疾病编码")
    private String organDiseaseId;

    @ItemProperty(alias = "支付标志")
    private Integer payFlag;

    @ItemProperty(alias = "支付日期")
    private Date payDate;

    @ItemProperty(alias = "结算单号")
    private Integer payListId;

    @ItemProperty(alias = "发药机构")
    private Integer giveOrgan;

    @ItemProperty(alias = "发药标志")
    private Integer giveFlag;

    @ItemProperty(alias = "发药完成日期")
    private Date giveDate;

    @ItemProperty(alias = "有效天数")
    private Integer valueDays;

    @ItemProperty(alias = "审核机构")
    @Dictionary(id = "eh.base.dictionary.Organ")
    private Integer checkOrgan;

    @ItemProperty(alias = "审核日期")
    private Date checkDate;

    @ItemProperty(alias = "审核人")
    @Dictionary(id = "eh.base.dictionary.Doctor")
    private Integer checker;

    @ItemProperty(alias = "人工审核日期")
    private Date checkDateYs;

    @ItemProperty(alias = "药师电话")
    private String checkerTel;

    @ItemProperty(alias = "支付方式")
    @Dictionary(id = "eh.cdr.dictionary.PayMode")
    private Integer payMode;

    @ItemProperty(alias = "发药方式")
    @Dictionary(id = "eh.cdr.dictionary.GiveMode")
    private Integer giveMode;

    @ItemProperty(alias = "发药人姓名")
    private String giveUser;

    @ItemProperty(alias = "签名的处方PDF")
    private Integer signFile;

    @ItemProperty(alias = "药师签名的处方PDF")
    private Integer chemistSignFile;

    @ItemProperty(alias = "收货人")
    private String receiver;

    @ItemProperty(alias = "收货人手机号")
    private String recMobile;

    @ItemProperty(alias = "收货人电话")
    private String recTel;

    @ItemProperty(alias = "地址（省）")
    @Dictionary(id = "eh.base.dictionary.AddrArea")
    private String address1;

    @ItemProperty(alias = "地址（市）")
    @Dictionary(id = "eh.base.dictionary.AddrArea")
    private String address2;

    @ItemProperty(alias = "地址（区县）")
    @Dictionary(id = "eh.base.dictionary.AddrArea")
    private String address3;

    @ItemProperty(alias = "详细地址")
    private String address4;

    @ItemProperty(alias = "邮政编码")
    private String zipCode;

    @ItemProperty(alias = "地址信息ID")
    private Integer addressId;

    @ItemProperty(alias = "处方状态")
    @Dictionary(id = "eh.cdr.dictionary.RecipeStatus")
    private Integer status;

    @ItemProperty(alias = "来源标志")
    @Dictionary(id = "eh.cdr.dictionary.FromFlag")
    private Integer fromflag;

    @ItemProperty(alias = "最后修改时间")
    private Date lastModify;

    @ItemProperty(alias = "开始配送时间")
    private Date startSendDate;

    @ItemProperty(alias = "开始发药时间")
    private Date sendDate;

    @ItemProperty(alias = "配送人")
    private String sender;

    @ItemProperty(alias = "签名时间")
    private Date signDate;

    @ItemProperty(alias = "处方药品名称集合")
    private String recipeDrugName;

    @ItemProperty(alias = "前台页面展示的时间")
    private Date recipeShowTime;

    @ItemProperty(alias = "微信端展示过期时间，处方离过期剩余小时数")
    private String recipeSurplusHours;

    @ItemProperty(alias = "审核失败备注")
    private String checkFailMemo;

    @ItemProperty(alias = "药师审核不通过，医生补充说明")
    private String supplementaryMemo;

    @ItemProperty(alias = "用户选择购药标志位")
    private Integer chooseFlag;

    @ItemProperty(alias = "处方失效前提醒标志位")
    private Integer remindFlag;

    @ItemProperty(alias = "交易流水号")
    private String tradeNo;

    @ItemProperty(alias = "微信支付方式")
    private String wxPayWay;

    @ItemProperty(alias = "商户订单号")
    private String outTradeNo;

    @ItemProperty(alias = "支付机构id")
    private String payOrganId;

    @ItemProperty(alias = "微信支付错误码")
    private String wxPayErrorCode;

    @ItemProperty(alias = "药企序号")
    private Integer enterpriseId;

    @ItemProperty(alias = "药企推送标志位, 0未推送，1已推送")
    private Integer pushFlag;

    @ItemProperty(alias = "药师审核不通过的旧处方Id")
    private Integer oldRecipeId;

    @ItemProperty(alias = "优惠券Id")
    private Integer couponId;

    @ItemProperty(alias = "最后需支付费用")
    private BigDecimal actualPrice;

    @ItemProperty(alias = "订单总价")
    private BigDecimal orderAmount;

    @ItemProperty(alias = "优惠价格")
    private String discountAmount;

    @ItemProperty(alias = "诊断备注")
    private String memo;

    @ItemProperty(alias = "医保支付标志，1：可以用医保")
    private Integer medicalPayFlag;

    @ItemProperty(alias = "配送处方标记 默认0，1: 只能配送")
    private Integer distributionFlag;

    @ItemProperty(alias = "处方备注")
    private String recipeMemo;

    @ItemProperty(alias = "中药属性：用法")
    private String tcmUsePathways;

    @ItemProperty(alias = "中药属性：用量")
    private String tcmUsingRate;

    @ItemProperty(alias = "处方单状态显示")
    private String showTip;

    @ItemProperty(alias = "药店价格最低价")
    private BigDecimal price1;

    @ItemProperty(alias = "药店价格最高价")
    private BigDecimal price2;

    @ItemProperty(alias = "医生姓名")
    private String doctorName;

    @ItemProperty(alias = "患者姓名")
    private String patientName;

    @ItemProperty(alias = "外带处方标志 1:外带药处方")
    private Integer takeMedicine;

    @ItemProperty(alias = "处方发起者id")
    private String requestMpiId;

    @ItemProperty(alias = "处方发起者urt")
    private Integer requestUrt;

    @ItemProperty(alias="当前clientId")
    private Integer currentClient;

    public Recipe() {
    }

    public Recipe(Integer recipeId, Integer clinicId, String mpiid,
                  Integer clinicOrgan, String recipeCode) {
        this.recipeId = recipeId;
        this.clinicId = clinicId;
        this.mpiid = mpiid;
        this.clinicOrgan = clinicOrgan;
        this.recipeCode = recipeCode;
    }

    public Recipe(Integer recipeId, String mpiid, Integer doctor, Date checkDate, Integer recipeType, Date signDate) {
        this.recipeId = recipeId;
        this.mpiid = mpiid;
        this.doctor = doctor;
        this.checkDate = checkDate;
        this.recipeType = recipeType;
        this.signDate = signDate;
    }

    public Recipe(Integer recipeId, Date signDate) {
        this.recipeId = recipeId;
        this.signDate = signDate;
    }

    public Recipe(Integer recipeId, Integer clinicId, String mpiid,
                  Integer clinicOrgan, String recipeCode, Integer recipeType,
                  Integer depart, Integer doctor, Date createDate, Integer copyNum,
                  BigDecimal totalMoney, String organDiseaseName, String organDiseaseId,
                  Integer payFlag, Date payDate, Integer payListId, Integer giveOrgan,
                  Integer giveFlag, Date giveDate, Integer valueDays,
                  Integer checkOrgan, Date checkDate, Integer checker,
                  Integer payMode, Integer giveMode, String giveUser,
                  Integer signFile, String receiver, String recMobile, String recTel,
                  String address1, String address2, String address3, String address4,
                  String zipCode, Integer addressId, Integer status, Integer fromflag, Date lastModify,
                  Date startSendDate, Date sendDate, Date signDate) {
        super();
        this.recipeId = recipeId;
        this.clinicId = clinicId;
        this.mpiid = mpiid;
        this.clinicOrgan = clinicOrgan;
        this.recipeCode = recipeCode;
        this.recipeType = recipeType;
        this.depart = depart;
        this.doctor = doctor;
        this.createDate = createDate;
        this.copyNum = copyNum;
        this.totalMoney = totalMoney;
        this.organDiseaseName = organDiseaseName;
        this.organDiseaseId = organDiseaseId;
        this.payFlag = payFlag;
        this.payDate = payDate;
        this.payListId = payListId;
        this.giveOrgan = giveOrgan;
        this.giveFlag = giveFlag;
        this.giveDate = giveDate;
        this.valueDays = valueDays;
        this.checkOrgan = checkOrgan;
        this.checkDate = checkDate;
        this.checker = checker;
        this.payMode = payMode;
        this.giveMode = giveMode;
        this.giveUser = giveUser;
        this.signFile = signFile;
        this.receiver = receiver;
        this.recMobile = recMobile;
        this.recTel = recTel;
        this.address1 = address1;
        this.address2 = address2;
        this.address3 = address3;
        this.address4 = address4;
        this.zipCode = zipCode;
        this.addressId = addressId;
        this.status = status;
        this.fromflag = fromflag;
        this.lastModify = lastModify;
        this.startSendDate = startSendDate;
        this.sendDate = sendDate;
        this.signDate = signDate;
    }

    public Recipe(Integer recipeId, Integer clinicId, String mpiid,
                  Integer clinicOrgan, String recipeCode, Integer recipeType,
                  Integer depart, Integer doctor, Date createDate, Integer copyNum,
                  BigDecimal totalMoney, String organDiseaseName, String organDiseaseId,
                  Integer payFlag, Date payDate, Integer payListId, Integer giveOrgan,
                  Integer giveFlag, Date giveDate, Integer valueDays,
                  Integer checkOrgan, Date checkDate, Integer checker,
                  Integer payMode, Integer giveMode, String giveUser,
                  Integer signFile, String receiver, String recMobile, String recTel,
                  String address1, String address2, String address3, String address4,
                  String zipCode, Integer addressId, Integer status, Integer fromflag, Date lastModify,
                  Date startSendDate, Date sendDate, Date signDate, String memo) {
        super();
        this.recipeId = recipeId;
        this.clinicId = clinicId;
        this.mpiid = mpiid;
        this.clinicOrgan = clinicOrgan;
        this.recipeCode = recipeCode;
        this.recipeType = recipeType;
        this.depart = depart;
        this.doctor = doctor;
        this.createDate = createDate;
        this.copyNum = copyNum;
        this.totalMoney = totalMoney;
        this.organDiseaseName = organDiseaseName;
        this.organDiseaseId = organDiseaseId;
        this.payFlag = payFlag;
        this.payDate = payDate;
        this.payListId = payListId;
        this.giveOrgan = giveOrgan;
        this.giveFlag = giveFlag;
        this.giveDate = giveDate;
        this.valueDays = valueDays;
        this.checkOrgan = checkOrgan;
        this.checkDate = checkDate;
        this.checker = checker;
        this.payMode = payMode;
        this.giveMode = giveMode;
        this.giveUser = giveUser;
        this.signFile = signFile;
        this.receiver = receiver;
        this.recMobile = recMobile;
        this.recTel = recTel;
        this.address1 = address1;
        this.address2 = address2;
        this.address3 = address3;
        this.address4 = address4;
        this.zipCode = zipCode;
        this.addressId = addressId;
        this.status = status;
        this.fromflag = fromflag;
        this.lastModify = lastModify;
        this.startSendDate = startSendDate;
        this.sendDate = sendDate;
        this.signDate = signDate;
        this.memo = memo;
    }

    @Column(name = "patientStatus")
    public int getPatientStatus() {
        return patientStatus;
    }

    public void setPatientStatus(int patientStatus) {
        this.patientStatus = patientStatus;
    }

    @Transient
    public BigDecimal getPrice1() {
        return price1;
    }

    public void setPrice1(BigDecimal price1) {
        this.price1 = price1;
    }

    @Transient
    public BigDecimal getPrice2() {
        return price2;
    }

    public void setPrice2(BigDecimal price2) {
        this.price2 = price2;
    }

    @Id
    @GeneratedValue(strategy = IDENTITY)
    @Column(name = "RecipeID", unique = true, nullable = false)
    public Integer getRecipeId() {
        return this.recipeId;
    }

    public void setRecipeId(Integer recipeId) {
        this.recipeId = recipeId;
    }

    @Column(name = "OrderCode")
    public String getOrderCode() {
        return orderCode;
    }

    public void setOrderCode(String orderCode) {
        this.orderCode = orderCode;
    }

    @Column(name = "ClinicID")
    public Integer getClinicId() {
        return this.clinicId;
    }

    public void setClinicId(Integer clinicId) {
        this.clinicId = clinicId;
    }

    @Column(name = "MPIID", nullable = false)
    public String getMpiid() {
        return this.mpiid;
    }

    public void setMpiid(String mpiid) {
        this.mpiid = mpiid;
    }

    @Column(name = "PatientID")
    public String getPatientID() {
        return patientID;
    }

    public void setPatientID(String patientID) {
        this.patientID = patientID;
    }

    @Column(name = "ClinicOrgan", nullable = false)
    public Integer getClinicOrgan() {
        return this.clinicOrgan;
    }

    public void setClinicOrgan(Integer clinicOrgan) {
        this.clinicOrgan = clinicOrgan;
    }

    @Column(name = "organName")
    public String getOrganName() {
        return organName;
    }

    public void setOrganName(String organName) {
        this.organName = organName;
    }

    @Column(name = "OriginClinicOrgan")
    public Integer getOriginClinicOrgan() {
        return originClinicOrgan;
    }

    public void setOriginClinicOrgan(Integer originClinicOrgan) {
        this.originClinicOrgan = originClinicOrgan;
    }

    @Column(name = "RecipeCode")
    public String getRecipeCode() {
        return this.recipeCode;
    }

    public void setRecipeCode(String recipeCode) {
        this.recipeCode = recipeCode;
    }

    @Column(name = "OriginRecipeCode")
    public String getOriginRecipeCode() {
        return originRecipeCode;
    }

    public void setOriginRecipeCode(String originRecipeCode) {
        this.originRecipeCode = originRecipeCode;
    }

    @Column(name = "RecipeType", length = 10)
    public Integer getRecipeType() {
        return this.recipeType;
    }

    public void setRecipeType(Integer recipeType) {
        this.recipeType = recipeType;
    }

    @Column(name = "Depart")
    public Integer getDepart() {
        return this.depart;
    }

    public void setDepart(Integer depart) {
        this.depart = depart;
    }

    @Column(name = "Doctor")
    public Integer getDoctor() {
        return this.doctor;
    }

    public void setDoctor(Integer doctor) {
        this.doctor = doctor;
    }

    @Column(name = "CreateDate")
    public Date getCreateDate() {
        return this.createDate;
    }

    public void setCreateDate(Date date) {
        this.createDate = date;
    }

    @Column(name = "CopyNum")
    public Integer getCopyNum() {
        return this.copyNum;
    }

    public void setCopyNum(Integer copyNum) {
        this.copyNum = copyNum;
    }

    @Column(name = "TotalMoney", precision = 10)
    public BigDecimal getTotalMoney() {
        return totalMoney;
    }

    public void setTotalMoney(BigDecimal totalMoney) {
        this.totalMoney = totalMoney;
    }

    @Column(name = "OrganDiseaseName")
    public String getOrganDiseaseName() {
        return this.organDiseaseName;
    }

    public void setOrganDiseaseName(String organDiseaseName) {
        this.organDiseaseName = organDiseaseName;
    }

    @Column(name = "OrganDiseaseID")
    public String getOrganDiseaseId() {
        return this.organDiseaseId;
    }

    public void setOrganDiseaseId(String organDiseaseId) {
        this.organDiseaseId = organDiseaseId;
    }

    @Column(name = "PayFlag")
    public Integer getPayFlag() {
        return this.payFlag;
    }

    public void setPayFlag(Integer payFlag) {
        this.payFlag = payFlag;
    }

    @Column(name = "PayDate")
    public Date getPayDate() {
        return this.payDate;
    }

    public void setPayDate(Date payDate) {
        this.payDate = payDate;
    }

    @Column(name = "PayListID")
    public Integer getPayListId() {
        return this.payListId;
    }

    public void setPayListId(Integer payListId) {
        this.payListId = payListId;
    }

    @Column(name = "GiveOrgan")
    public Integer getGiveOrgan() {
        return this.giveOrgan;
    }

    public void setGiveOrgan(Integer giveOrgan) {
        this.giveOrgan = giveOrgan;
    }

    @Column(name = "GiveFlag")
    public Integer getGiveFlag() {
        return this.giveFlag;
    }

    public void setGiveFlag(Integer giveFlag) {
        this.giveFlag = giveFlag;
    }

    @Column(name = "CheckOrgan")
    public Integer getCheckOrgan() {
        return checkOrgan;
    }

    public void setCheckOrgan(Integer checkOrgan) {
        this.checkOrgan = checkOrgan;
    }

    @Column(name = "CheckDate")
    public Date getCheckDate() {
        return checkDate;
    }

    public void setCheckDate(Date checkDate) {
        this.checkDate = checkDate;
    }

    @Column(name = "Checker")
    public Integer getChecker() {
        return checker;
    }

    public void setChecker(Integer checker) {
        this.checker = checker;
    }

    @Column(name = "CheckDateYs")
    public Date getCheckDateYs() {
        return checkDateYs;
    }

    public void setCheckDateYs(Date checkDateYs) {
        this.checkDateYs = checkDateYs;
    }

    @Transient
    public String getCheckerTel() {
        return checkerTel;
    }

    public void setCheckerTel(String checkerTel) {
        this.checkerTel = checkerTel;
    }

    @Column(name = "PayMode")
    public Integer getPayMode() {
        return payMode;
    }

    public void setPayMode(Integer payMode) {
        this.payMode = payMode;
    }

    @Column(name = "GiveMode")
    public Integer getGiveMode() {
        return giveMode;
    }

    public void setGiveMode(Integer giveMode) {
        this.giveMode = giveMode;
    }

    @Column(name = "GiveUser", length = 20)
    public String getGiveUser() {
        return giveUser;
    }

    public void setGiveUser(String giveUser) {
        this.giveUser = giveUser;
    }

    @Column(name = "SignFile")
    public Integer getSignFile() {
        return signFile;
    }

    public void setSignFile(Integer signFile) {
        this.signFile = signFile;
    }

    @Column(name = "ChemistSignFile")
    public Integer getChemistSignFile() {
        return chemistSignFile;
    }

    public void setChemistSignFile(Integer chemistSignFile) {
        this.chemistSignFile = chemistSignFile;
    }

    @Column(name = "Receiver")
    public String getReceiver() {
        return receiver;
    }

    public void setReceiver(String receiver) {
        this.receiver = receiver;
    }

    @Column(name = "recMobile")
    public String getRecMobile() {
        return recMobile;
    }

    public void setRecMobile(String recMobile) {
        this.recMobile = recMobile;
    }

    @Column(name = "RecTel")
    public String getRecTel() {
        return recTel;
    }

    public void setRecTel(String recTel) {
        this.recTel = recTel;
    }

    @Column(name = "Address1")
    public String getAddress1() {
        return address1;
    }

    public void setAddress1(String address1) {
        this.address1 = address1;
    }

    @Column(name = "Address2")
    public String getAddress2() {
        return address2;
    }

    public void setAddress2(String address2) {
        this.address2 = address2;
    }

    @Column(name = "Address3")
    public String getAddress3() {
        return address3;
    }

    public void setAddress3(String address3) {
        this.address3 = address3;
    }

    @Column(name = "Address4")
    public String getAddress4() {
        return address4;
    }

    public void setAddress4(String address4) {
        this.address4 = address4;
    }

    @Column(name = "ZipCode")
    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    @Column(name = "AddressID")
    public Integer getAddressId() {
        return addressId;
    }

    public void setAddressId(Integer addressId) {
        this.addressId = addressId;
    }

    @Column(name = "Status")
    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    @Column(name = "GiveDate")
    public Date getGiveDate() {
        return this.giveDate;
    }

    public void setGiveDate(Date giveDate) {
        this.giveDate = giveDate;
    }

    @Column(name = "ValueDays")
    public Integer getValueDays() {
        return this.valueDays;
    }

    public void setValueDays(Integer valueDays) {
        this.valueDays = valueDays;
    }

    @Column(name = "fromflag")
    public Integer getFromflag() {
        return fromflag;
    }

    public void setFromflag(Integer fromflag) {
        this.fromflag = fromflag;
    }

    @Column(name = "LastModify")
    public Date getLastModify() {
        return this.lastModify;
    }

    public void setLastModify(Date lastModify) {
        this.lastModify = lastModify;
    }

    @Column(name = "startSendDate")
    public Date getStartSendDate() {
        return this.startSendDate;
    }

    public void setStartSendDate(Date startSendDate) {
        this.startSendDate = startSendDate;
    }

    @Column(name = "sendDate")
    public Date getSendDate() {
        return this.sendDate;
    }

    public void setSendDate(Date sendDate) {
        this.sendDate = sendDate;
    }

    @Column(name = "SignDate")
    public Date getSignDate() {
        return signDate;
    }

    public void setSignDate(Date signDate) {
        this.signDate = signDate;
    }

    @Transient
    public String getRecipeDrugName() {
        return recipeDrugName;
    }

    public void setRecipeDrugName(String recipeDrugName) {
        this.recipeDrugName = recipeDrugName;
    }

    @Transient
    public Date getRecipeShowTime() {
        return recipeShowTime;
    }

    public void setRecipeShowTime(Date recipeShowTime) {
        this.recipeShowTime = recipeShowTime;
    }

    @Transient
    public String getRecipeSurplusHours() {
        return recipeSurplusHours;
    }

    public void setRecipeSurplusHours(String recipeSurplusHours) {
        this.recipeSurplusHours = recipeSurplusHours;
    }

    @Column(name = "CheckFailMemo")
    public String getCheckFailMemo() {
        return checkFailMemo;
    }

    public void setCheckFailMemo(String checkFailMemo) {
        this.checkFailMemo = checkFailMemo;
    }

    @Column(name = "SupplementaryMemo")
    public String getSupplementaryMemo() {
        return supplementaryMemo;
    }

    public void setSupplementaryMemo(String supplementaryMemo) {
        this.supplementaryMemo = supplementaryMemo;
    }

    @Column(name = "ChooseFlag")
    public Integer getChooseFlag() {
        return chooseFlag;
    }

    public void setChooseFlag(Integer chooseFlag) {
        this.chooseFlag = chooseFlag;
    }

    @Column(name = "RemindFlag")
    public Integer getRemindFlag() {
        return remindFlag;
    }

    public void setRemindFlag(Integer remindFlag) {
        this.remindFlag = remindFlag;
    }

    @Column(name = "TradeNo")
    public String getTradeNo() {
        return tradeNo;
    }

    public void setTradeNo(String tradeNo) {
        this.tradeNo = tradeNo;
    }

    @Column(name = "WxPayWay")
    public String getWxPayWay() {
        return wxPayWay;
    }

    public void setWxPayWay(String wxPayWay) {
        this.wxPayWay = wxPayWay;
    }

    @Column(name = "WxPayErrorCode")
    public String getWxPayErrorCode() {
        return wxPayErrorCode;
    }

    public void setWxPayErrorCode(String wxPayErrorCode) {
        this.wxPayErrorCode = wxPayErrorCode;
    }

    @Column(name = "OutTradeNo")
    public String getOutTradeNo() {
        return outTradeNo;
    }

    public void setOutTradeNo(String outTradeNo) {
        this.outTradeNo = outTradeNo;
    }

    @Column(name = "Sender")
    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    @Column(name = "EnterpriseId")
    public Integer getEnterpriseId() {
        return enterpriseId;
    }

    public void setEnterpriseId(Integer enterpriseId) {
        this.enterpriseId = enterpriseId;
    }

    @Column(name = "PushFlag")
    public Integer getPushFlag() {
        return pushFlag;
    }

    public void setPushFlag(Integer pushFlag) {
        this.pushFlag = pushFlag;
    }

    @Column(name = "payOrganId")
    public String getPayOrganId() {
        return payOrganId;
    }

    public void setPayOrganId(String payOrganId) {
        this.payOrganId = payOrganId;
    }

    @Column(name = "OldRecipeId")
    public Integer getOldRecipeId() {
        return oldRecipeId;
    }

    public void setOldRecipeId(Integer oldRecipeId) {
        this.oldRecipeId = oldRecipeId;
    }

    @Column(name = "CouponId")
    public Integer getCouponId() {
        return couponId;
    }

    public void setCouponId(Integer couponId) {
        this.couponId = couponId;
    }

    @Column(name = "ActualPrice")
    public BigDecimal getActualPrice() {
        return actualPrice;
    }

    public void setActualPrice(BigDecimal actualPrice) {
        this.actualPrice = actualPrice;
    }

    @Transient
    public BigDecimal getOrderAmount() {
        return orderAmount;
    }

    public void setOrderAmount(BigDecimal orderAmount) {
        this.orderAmount = orderAmount;
    }

    @Transient
    public String getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(String discountAmount) {
        this.discountAmount = discountAmount;
    }

    @Column(name = "Memo")
    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    @Column(name = "MedicalPayFlag")
    public Integer getMedicalPayFlag() {
        return medicalPayFlag;
    }

    public void setMedicalPayFlag(Integer medicalPayFlag) {
        this.medicalPayFlag = medicalPayFlag;
    }

    public boolean canMedicalPay() {
        Integer useMedicalFlag = 1;
        return (useMedicalFlag.equals(medicalPayFlag)) ? true : false;
    }

    @Column(name = "DistributionFlag")
    public Integer getDistributionFlag() {
        return distributionFlag;
    }

    public void setDistributionFlag(Integer distributionFlag) {
        this.distributionFlag = distributionFlag;
    }

    public boolean onlyDistribution() {
        Integer distribution = 1;
        return (distribution.equals(distributionFlag)) ? true : false;
    }

    @Column(name = "RecipeMemo")
    public String getRecipeMemo() {
        return recipeMemo;
    }

    public void setRecipeMemo(String recipeMemo) {
        this.recipeMemo = recipeMemo;
    }

    @Transient
    public String getTcmUsePathways() {
        return tcmUsePathways;
    }

    public void setTcmUsePathways(String tcmUsePathways) {
        this.tcmUsePathways = tcmUsePathways;
    }

    @Transient
    public String getTcmUsingRate() {
        return tcmUsingRate;
    }

    public void setTcmUsingRate(String tcmUsingRate) {
        this.tcmUsingRate = tcmUsingRate;
    }

    @Transient
    public String getShowTip() {
        return showTip;
    }

    public void setShowTip(String showTip) {
        this.showTip = showTip;
    }

    @Column(name = "doctorName")
    public String getDoctorName() {
        return doctorName;
    }

    public void setDoctorName(String doctorName) {
        this.doctorName = doctorName;
    }

    @Column(name = "patientName")
    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    @Column(name = "TakeMedicine")
    public Integer getTakeMedicine() {
        return takeMedicine;
    }

    public void setTakeMedicine(Integer takeMedicine) {
        this.takeMedicine = takeMedicine;
    }

    @Column(name = "requestMpiId")
    public String getRequestMpiId() {
        return requestMpiId;
    }

    public void setRequestMpiId(String requestMpiId) {
        this.requestMpiId = requestMpiId;
    }

    @Column(name = "requestUrt")
    public Integer getRequestUrt() {
        return requestUrt;
    }

    public void setRequestUrt(Integer requestUrt) {
        this.requestUrt = requestUrt;
    }

    @Column(name = "currentClient")
    public Integer getCurrentClient() {
        return currentClient == null ? ClientSession.getCurrentId() : currentClient;
    }

    public void setCurrentClient(Integer currentClient) {
        this.currentClient = currentClient;
    }
}