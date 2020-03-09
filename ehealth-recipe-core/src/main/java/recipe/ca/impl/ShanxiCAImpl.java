
package recipe.ca.impl;

import com.ngari.his.ca.model.*;
import com.ngari.patient.dto.DoctorDTO;
import com.ngari.patient.service.BasicAPI;
import com.ngari.patient.service.DoctorService;
import com.ngari.recipe.entity.Recipe;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import eh.base.constant.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.ca.CAInterface;
import recipe.ca.ICommonCAServcie;
import recipe.ca.vo.CaSignResultVo;
import recipe.util.RedisClient;

/**
 * CA标准化对接文档
 */
@RpcBean("shanxiCA")
public class ShanxiCAImpl implements CAInterface {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShanxiCAImpl.class);
    private ICommonCAServcie iCommonCAServcie= AppContextHolder.getBean("iCommonCAServcie", ICommonCAServcie.class);
    /**
     * CA用户注册、申请证书接口
     * @param doctorId
     * @return
     */
    @RpcService
    public boolean caUserLoginAndGetCertificate(Integer doctorId){
        LOGGER.info("ShanxiCAImpl caUserLoginAndGetCertificate start in doctorId={}", doctorId);
        //根据doctorId获取医生信息
        boolean isSuccess = false;
        DoctorService doctorDAO = BasicAPI.getService(DoctorService.class);
        DoctorDTO doctor = doctorDAO.getByDoctorId(doctorId);
        if (null == doctor) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "doctorId 找不到改医生");
        }
        CaAccountRequestTO requestTO = new CaAccountRequestTO();
        requestTO.setIdCard(doctor.getIdNumber());
        requestTO.setMobile(doctor.getMobile());
        requestTO.setOrganId(doctor.getOrgan());
        requestTO.setUserAccount(doctor.getIdNumber());
        requestTO.setUserEmail(doctor.getEmail());
        requestTO.setUserName(doctor.getName());
        requestTO.setIdNoType("1");
        try {
            //用户操作类型 * 1.用户注册 * 2.用户修改 * 3.用户查询
            requestTO.setBusType(3);
            isSuccess = iCommonCAServcie.caUserBusiness(requestTO);
            if (!isSuccess) {
                requestTO.setBusType(1);
                isSuccess = iCommonCAServcie.caUserBusiness(requestTO);
                LOGGER.info("ShanxiCAImpl caUserLoginAndGetCertificate end isSuccess={}", isSuccess);
                isSuccess = true;
            }
        } catch (Exception e){
            LOGGER.error("ShanxiCAImpl caUserLoginAndGetCertificate 调用前置机失败 requestTO={}", JSONUtils.toString(requestTO));
            e.printStackTrace();
        }
        return isSuccess;
    }

    @RpcService
    public boolean caPasswordBusiness(CaPasswordRequestTO requestTO) {
        RedisClient redisClient = AppContextHolder.getBean("redisClient", RedisClient.class);
        boolean isSuccess= false;
        //用户操作类型 * 1.设置密码 * 2.修改密码 * 3.找回密码，4.查询是否设置密码，5.验证密码是否正确
        if (4 == requestTO.getBusType()) {
            if (null != redisClient.get("password_" + requestTO.getUserAccount())) {
                return true;
            }
        } else {
            isSuccess = iCommonCAServcie.caPasswordBusiness(requestTO);
            if (isSuccess) {
                redisClient.set("password_" + requestTO.getUserAccount(),"yes");
            }
        }
        return isSuccess;
    }

    /**
     * 标准化CA签名及签章接口
     * @param requestSealTO
     * @param organId
     * @param userAccount
     * @param caPassword
     */
    @RpcService
    public CaSignResultVo commonCASignAndSeal(CaSealRequestTO requestSealTO, Recipe recipe,Integer organId, String userAccount, String caPassword) {
        LOGGER.info("recipe服务 commonCASignAndSeal start requestSealTO={},organId={},userAccount={},caPassword={}", JSONUtils.toString(requestSealTO), organId, userAccount, caPassword);
        CaSignResultVo signResultVo = new CaSignResultVo();

        try {
            //电子签名（暂不实现）

            //上传手签图片
            CaPictureRequestTO pictureRequestTO = new CaPictureRequestTO();
            pictureRequestTO.setOrganId(organId);
            pictureRequestTO.setUserAccount(userAccount);
            //获取手签图片
            pictureRequestTO.setSealPicture(requestSealTO.getSealBase64Str());
            // 用户操作类型 * 1.上传图片 * 2.修改图片 * 3.查询图片 * 4.注销图片
            pictureRequestTO.setBusType(3);
            boolean isSuccess = iCommonCAServcie.caPictureBusiness(pictureRequestTO);
            if (!isSuccess) {
                 //上传图片
                 pictureRequestTO.setBusType(1);
                 isSuccess = iCommonCAServcie.caPictureBusiness(pictureRequestTO);
                 if(!isSuccess){
                     LOGGER.info("caPictureBusiness 上传图片失败 ");
                     return null;
                 }
            }

            //获取时间戳数据
            CaSignDateRequestTO caSignDateRequestTO = new CaSignDateRequestTO();
            caSignDateRequestTO.setOrganId(organId);
            caSignDateRequestTO.setSignMsg(JSONUtils.toString(recipe));
            caSignDateRequestTO.setUserAccount(userAccount);
            CaSignDateResponseTO responseDateTO = iCommonCAServcie.caSignDateBusiness(caSignDateRequestTO);
            if (responseDateTO != null) {
                signResultVo.setSignCADate(responseDateTO.getSignDate());
            }

            //电子签章业务
            requestSealTO.setOrganId(organId);
            requestSealTO.setUserAccount(userAccount);
            requestSealTO.setUserPin(caPassword);
            requestSealTO.setCertMsg(null);
            requestSealTO.setRightX(1);
            requestSealTO.setRightY(1);
            requestSealTO.setKeyWord("");
            requestSealTO.setSzIndexes(0);
            CaSealResponseTO responseSealTO = iCommonCAServcie.caSealBusiness(requestSealTO);

            if (responseSealTO != null){
                signResultVo.setPdfBase64(responseSealTO.getPdfBase64File());
            }
        } catch (Exception e){
            LOGGER.error("esign 服务 commonCASignAndSeal 调用前置机失败 requestTO={}", requestSealTO.toString());
            e.printStackTrace();
        }
        return signResultVo;
    }




}
