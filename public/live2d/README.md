# A1 奶霜丝带 · Live2D 模型接入位

目前角色素材是原画与 Q 版表情图，不包含 Live2D 分层图、`.moc3`、`.model3.json`、纹理、物理参数或动作数据。
**仅放入单张 PNG 并不能得到真正的骨骼/面部形变动画。**

后续按许可合法取得 Live2D Cubism SDK for Web，制作分层立绘，并导出以下文件：

```
public/live2d/a1.model3.json
public/live2d/a1.moc3
public/live2d/texture_00.png
public/live2d/physics3.json
public/live2d/idle.motion3.json
public/live2d/cheer.motion3.json
public/live2d/rest.motion3.json
```

可用 `src/live2d.ts` 的角色事件接口，将 `idle / cheer / rest / ready / done / touch` 映射为正式动作。
分发 SDK 时需要遵守 Live2D 对应版本的 SDK 及运行时许可，不直接复制第三方角色模型。

V4.2 自带可运行的轻量动态角色及语音情绪状态切换，但不冒充 Live2D 模型。
