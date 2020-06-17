package recipe.ca.impl;

import com.ngari.his.ca.model.*;
import com.ngari.patient.dto.DoctorDTO;
import com.ngari.patient.service.BasicAPI;
import com.ngari.patient.service.DoctorService;
import com.ngari.patient.service.EmploymentService;
import com.ngari.recipe.entity.Recipe;
import ctd.util.AppContextHolder;
import ctd.util.JSONUtils;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import recipe.ApplicationUtils;
import recipe.ca.CAInterface;
import recipe.ca.ICommonCAServcie;
import recipe.ca.vo.CaSignResultVo;

import java.util.List;

/**
 * CA标准化对接文档
 */
@RpcBean("shanghaiCA")
public class ShanghaiCAImpl implements CAInterface {
    private static final Logger LOGGER = LoggerFactory.getLogger(ShanghaiCAImpl.class);

    private ICommonCAServcie iCommonCAServcie= AppContextHolder.getBean("iCommonCAServcie", ICommonCAServcie.class);

    private DoctorService doctorService = ApplicationUtils.getBasicService(DoctorService.class);
    /**
     * CA用户注册、申请证书接口
     * @param doctorId
     * @return
     */
    @RpcService
    public boolean caUserLoginAndGetCertificate(Integer doctorId){
        DoctorDTO doctorDTO = doctorService.getByDoctorId(doctorId);

        CaAccountRequestTO caAccountRequestTO = new CaAccountRequestTO();
        caAccountRequestTO.setOrganId(doctorDTO.getOrgan());
        caAccountRequestTO.setUserName(doctorDTO.getName());
        return iCommonCAServcie.caUserBusiness(caAccountRequestTO);
    }

    /**
     * CA密码接口
     * @param requestTO
     * @return
     */
    @RpcService
    public boolean caPasswordBusiness(CaPasswordRequestTO requestTO) {
        return true;
    }

    /**
     * 标准化CA签名及签章接口
     * @param requestSealTO
     * @param organId
     * @param userAccount
     * @param caPassword
     */
    @RpcService
    public CaSignResultVo commonCASignAndSeal(CaSealRequestTO requestSealTO, Recipe recipe, Integer organId, String userAccount, String caPassword) {
        LOGGER.info("shanghaiCA commonCASignAndSeal start requestSealTO={},recipeId={},organId={},userAccount={},caPassword={}",
                JSONUtils.toString(requestSealTO), recipe.getRecipeId(),organId, userAccount, caPassword);
        CaSignResultVo signResultVo = new CaSignResultVo();
        try {
            EmploymentService employmentService = BasicAPI.getService(EmploymentService.class);
            List<String> jobNumbers = employmentService.findJobNumberByDoctorIdAndOrganId(recipe.getDoctor(), recipe.getClinicOrgan());
            //电子签名
            CaSignRequestTO caSignRequestTO = new CaSignRequestTO();
            caSignRequestTO.setCretMsg(null);
            caSignRequestTO.setOrganId(organId);
            if (!CollectionUtils.isEmpty(jobNumbers)) {
                caSignRequestTO.setJobnumber(jobNumbers.get(0));
            }
            caSignRequestTO.setSignMsg(JSONUtils.toString(recipe));
            caSignRequestTO.setUserAccount(userAccount);
            CaSignResponseTO responseTO = iCommonCAServcie.caSignBusiness(caSignRequestTO);
            if (responseTO == null || responseTO.getCode() != 200) {
                signResultVo.setCode(responseTO.getCode());
                signResultVo.setMsg(responseTO.getMsg());
                return signResultVo;
            }
            signResultVo.setSignRecipeCode(responseTO.getSignValue());
            //上传手签图片(暂不实现)

            //获取时间戳数据
            CaSignDateRequestTO caSignDateRequestTO = new CaSignDateRequestTO();
            caSignDateRequestTO.setOrganId(organId);
            caSignDateRequestTO.setUserAccount(userAccount);
            caSignDateRequestTO.setSignMsg(JSONUtils.toString(recipe));

            CaSignDateResponseTO responseDateTO = iCommonCAServcie.caSignDateBusiness(caSignDateRequestTO);
            if (responseDateTO == null || responseDateTO.getCode() != 200) {
                signResultVo.setCode(responseDateTO.getCode());
                signResultVo.setMsg(responseDateTO.getMsg());
                return signResultVo;
            }
            signResultVo.setSignCADate(responseDateTO.getSignDate());
            signResultVo.setCode(200);

            // 电子签章
            requestSealTO.setOrganId(organId);
            requestSealTO.setUserPin(caPassword);
            requestSealTO.setUserAccount(userAccount);
            requestSealTO.setDoctorType(null == recipe.getChecker() ? "0" : "1");
            requestSealTO.setSignMsg(JSONUtils.toString(recipe));
            if (!CollectionUtils.isEmpty(jobNumbers)) {
                requestSealTO.setJobnumber(jobNumbers.get(0));
            }
            CaSealResponseTO responseSealTO = iCommonCAServcie.caSealBusiness(requestSealTO);

            if (responseSealTO == null || (responseSealTO.getCode() != 200
                    && requestSealTO.getCode() != 404 && requestSealTO.getCode() != 405)){
                signResultVo.setCode(responseSealTO.getCode());
                signResultVo.setMsg(responseSealTO.getMsg());
                return signResultVo;
            }
            signResultVo.setPdfBase64(responseSealTO.getPdfBase64File());
        } catch (Exception e){
            LOGGER.error("ShanghaiCAImpl commonCASignAndSeal 调用前置机失败 requestSealTO={},recipeId={},organId={},userAccount={},caPassword={}",
                    JSONUtils.toString(requestSealTO), recipe.getRecipeId(),organId, userAccount, caPassword,e);
            LOGGER.error("commonCASignAndSeal Exception", e);
        }
        LOGGER.info("shanghaiCA commonCASignAndSeal end recipeId={},params: {}", recipe.getRecipeId(),JSONUtils.toString(signResultVo));
        return signResultVo;
    }
}
