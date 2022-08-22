package recipe.retry;

import com.github.rholder.retry.Retryer;
import com.github.rholder.retry.RetryerBuilder;
import com.github.rholder.retry.StopStrategies;
import com.github.rholder.retry.WaitStrategies;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.recipe.mode.PayNotifyReqTO;
import com.ngari.his.recipe.mode.PayNotifyResTO;
import ctd.util.JSONUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import recipe.client.PayClient;
import recipe.presettle.settle.IRecipeSettleService;

import java.util.concurrent.TimeUnit;

/**
 * created by shiyuping on 2020/12/9
 *
 * @author shiyuping
 */
@Service
public class RecipeRetryService {
    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeRetryService.class);
    @Autowired
    private PayClient payClient;

    /**
     * 结算异常处理----补偿机制--异常将重试三次
     *
     * @param settleService
     * @param req
     * @return
     */
    public PayNotifyResTO doRecipeSettle(IRecipeSettleService settleService, PayNotifyReqTO req) {
        LOGGER.info("RecipeSettleRetryService.doRecipeSettle start req:{}", JSONUtils.toString(req));
        Retryer<PayNotifyResTO> retryer = RetryerBuilder.<PayNotifyResTO>newBuilder()
                //抛出指定异常重试
                .retryIfExceptionOfType(Exception.class)
                //停止重试策略
                .withStopStrategy(StopStrategies.stopAfterAttempt(3))
                //每次等待重试时间间隔
                .withWaitStrategy(WaitStrategies.fixedWait(300, TimeUnit.MILLISECONDS))
                .build();
        PayNotifyResTO resTO = new PayNotifyResTO();
        try {
            resTO = retryer.call(() -> {
                LOGGER.info("RecipeSettleRetryService.doRecipeSettle retry");
                return settleService.recipeSettle(req);
            });
        } catch (Exception e) {
            // 异常启动结算反查机制
            HisResponseTO hisResponseTO = payClient.retrySettle(req);
            // 反查成功或异常都算结算成功
            if("99".equals(hisResponseTO.getMsgCode()) || "200".equals(hisResponseTO.getMsgCode())){
                resTO.setMsgCode(0);
            }
            return resTO;
        }
        return resTO;
    }

}
