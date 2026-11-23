package org.caojun.ai.translate.provider;

import org.caojun.ai.translate.provider.ITranslateCallback;

/**
 * 翻译 provider 的统一契约。
 *
 * 与旧的 {@code org.caojun.ai.translate.api.ITranslateService} 并存：
 * 旧接口只服务内置 Marian 的 OnDeviceTranslate，新接口面向「多模型 / 动态语种」的 provider。
 */
interface ITranslateProvider {

    /** 契约版本。 */
    int getApiVersion();

    /** 能力描述，JSON 字符串，见 TranslateCapability。 */
    String getCapability();

    /** 预加载指定方向的模型（异步，失败只记日志）。 */
    void preload(String from, String to);

    /**
     * 翻译。结果通过 callback 返回。
     *
     * @param optionsJson 预留：术语表、上下文等，可传空串
     */
    void translate(String text, String from, String to, String optionsJson,
                   ITranslateCallback callback);
}
