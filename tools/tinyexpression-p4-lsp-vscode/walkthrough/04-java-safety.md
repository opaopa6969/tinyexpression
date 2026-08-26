# Javaは「書ける」と「実行できる」を分けます

フェンス付きJavaブロックはJavaとして色分けされます。一方、Javaは式DSLの権限を越えて任意コードを実行できるため、DAPのコンパイル・実行は既定で無効です。

`allowJavaCodeBlocks: true` は、入力作者を信頼でき、プロセスやコンテナが十分に隔離されている場合だけ使用します。公開のTinyExpression Editorでは無効のままです。
