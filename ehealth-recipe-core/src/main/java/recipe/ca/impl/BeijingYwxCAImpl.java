package recipe.ca.impl;

import com.alibaba.fastjson.JSONObject;
import com.ngari.base.doctor.model.DoctorBean;
import com.ngari.base.doctor.service.IDoctorService;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.ca.model.*;
import com.ngari.his.ca.service.ICaHisService;
import com.ngari.his.common.service.ICommonHisService;
import com.ngari.patient.dto.DoctorDTO;
import com.ngari.patient.service.BasicAPI;
import com.ngari.patient.service.DoctorService;
import com.ngari.recipe.entity.Recipe;
import ctd.persistence.exception.DAOException;
import ctd.util.AppContextHolder;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.lang.StringUtils;
import org.apache.poi.util.StringUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import recipe.ca.CAInterface;
import recipe.ca.ICommonCAServcie;
import recipe.ca.vo.CaSignResultVo;
import recipe.util.RedisClient;

import javax.print.Doc;

@RpcBean("BeijingYCA")
public class BeijingYwxCAImpl{

    private RedisClient redisClient = AppContextHolder.getBean("redisClient", RedisClient.class);
    private ICommonCAServcie iCommonCAServcie = AppContextHolder.getBean("iCommonCAServcie", ICommonCAServcie.class);
    private static ICaHisService iCaHisService = AppContextHolder.getBean("his.iCaHisService",ICaHisService.class);
    private Logger logger = LoggerFactory.getLogger(BeijingYwxCAImpl.class);
    private String AccessToken_KEY = "BjYCAToken";
    private DoctorService doctorService = BasicAPI.getService(DoctorService.class);


    /**
     * 获取Token接口
     * @param organId
     * @return
     */
    @RpcService
    public String caTokenBussiness(Integer organId) {
        String redisKey = AccessToken_KEY +"_"+ Integer.toString(organId);
        if (null == redisClient.get(redisKey)) {
            CaTokenRequestTo requestTO = new CaTokenRequestTo();
            requestTO.setOrganId(organId);
            CaTokenResponseTo responseTO = iCommonCAServcie.newCaTokenBussiness(requestTO);
            if (StringUtils.isNotEmpty(responseTO.getToken()) && StringUtils.isNotEmpty(responseTO.getExpireTime())) {
                Long timeOut = Long.parseLong(responseTO.getExpireTime());
                redisClient.setEX(redisKey, timeOut, responseTO.getToken());
            }
        }
        return redisClient.get(redisKey);
    }

    @RpcService
    public CaAccountResponseTO getDocStatus(String openId,Integer organId){
      CaAccountRequestTO requestTO = new CaAccountRequestTO();
      CaAccountResponseTO responseTO = new CaAccountResponseTO();
      requestTO.setOrganId(organId);
      requestTO.setUserAccount(openId);
      String token = caTokenBussiness(organId);
      requestTO.setUserName(token);
      requestTO.setBusType(0);

      HisResponseTO<CaAccountResponseTO> responseTO1 =  iCaHisService.caUserBusiness(requestTO);
        logger.info("getDocStatus result info={}", JSONObject.toJSONString(responseTO1));
        if (null != responseTO1 && "200".equals(responseTO1.getMsgCode())) {

            responseTO.setUserAccount(responseTO1.getData().getUserAccount());
            responseTO.setUserStatus(responseTO1.getData().getUserStatus());
            responseTO.setExtraValue(responseTO1.getData().getExtraValue());
            responseTO.setStamp(responseTO1.getData().getStamp());
            return responseTO;

        }else {
            logger.error("前置机未返回数据");
        }

     return null;

    }

    /**
     * 获取开启自动签名的状态
     *
     * @param organId
     * @param doctorId
     * @return
     */
    @RpcService
    public Boolean getAutoSignStatus(Integer organId, Integer doctorId) {
        CaAccountResponseTO responseTO = getDocStatusForPC(organId, doctorId);
        CaAutoSignRequestTO requestTO = new CaAutoSignRequestTO();
        requestTO.setToken(caTokenBussiness(organId));
        requestTO.setOrganId(organId);
        requestTO.setBussType(0);
        requestTO.setOpenId(responseTO.getUserAccount());
        CaAutoSignResponseTO result = iCommonCAServcie.caAutoSignBusiness(requestTO);
        if (result != null && "200".equals(result.getCode())) {
            return result.getAutoSign();
        }
        return false;
    }


    private CaAccountResponseTO getDocStatusForPC(Integer organId, Integer doctorId) {
        DoctorDTO doctorDTO = doctorService.get(doctorId);
        if (doctorDTO == null) {
            throw new DAOException(609, "该医生不存在");
        }
        CaAccountRequestTO requestTO = new CaAccountRequestTO();
        CaAccountResponseTO responseTO = new CaAccountResponseTO();
        requestTO.setIdNoType("SF");
        requestTO.setIdCard(doctorDTO.getIdNumber());
        requestTO.setUserName(caTokenBussiness(organId));
        requestTO.setBusType(0);
        responseTO = iCommonCAServcie.caUserBusinessNew(requestTO);
        if (responseTO != null && "200".equals(responseTO.getCode())) {
            return responseTO;
        } else {
            logger.info("前置机未返回数据");
        }
        return responseTO;
    }

    /**
     * 授权开启自动签名
     * @param openId
     * @param organId
     * @return
     */
    @RpcService
    public Boolean openAutoSign(String openId, Integer organId) {
        CaAutoSignRequestTO requestTO = new CaAutoSignRequestTO();
        requestTO.setOpenId(openId);
        requestTO.setToken(caTokenBussiness(organId));
        requestTO.setOrganId(organId);
        requestTO.setSessionTime(1);
        requestTO.setBussType(1);
        CaAutoSignResponseTO response = iCommonCAServcie.caAutoSignBusiness(requestTO);
        if (response != null && "200".equals(response.getCode())
                && StringUtils.isNotEmpty(response.getAutoSignPicture())) {
            return true;
        }
        return false;
    }
}
