package com.valoser.futacha.shared.ui.compat

/**
 * User-facing Futacha release history derived from the private release-note source.
 * The embedded view intentionally shows only version headings and changes.
 */
internal val FUTACHA_CHANGE_LOG_HTML: String = """
    <!doctype html>
    <html lang="ja">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
            body {
                margin: 0;
                padding: 12px 16px 28px;
                background: #ffffff;
                color: #202124;
                font-family: sans-serif;
                font-size: 18px;
                line-height: 1.7;
            }
            h2 {
                margin: 22px 0 6px;
                color: #00796b;
                font-size: 24px;
            }
            h2:first-child { margin-top: 0; }
            ul { margin: 0; padding-left: 1.4em; }
            li { margin: 0 0 6px; }
        </style>
    </head>
    <body>
    <h2>11.5</h2>
    <ul>
        <li>スレッドの保存中にアプリを切り替えても保存が止まりにくくしました。Androidですぐに終わる保存でアプリが終了することがある問題と、iOSで保存を取り消したあとにバックグラウンド処理が残る問題を修正しました。</li>
        <li>スレッドの読み位置が保存されない、または別のレスの位置で保存されることがある問題を修正しました。</li>
        <li>レス数が変わらない更新や「更新前に戻す」で、スレッドの内容や期限・削除の表示が古いまま残る問題と、バックグラウンドのタブ更新が直前の変更を古い内容で上書きすることがある問題を修正しました。</li>
        <li>ふたちゃモードでスレッドを開いた直後に、NGで隠すはずのレスが一瞬表示されることがある問題を修正しました。</li>
        <li>Android 14・15のジェスチャーナビゲーションで、スレッドの左端からのスワイプが履歴を開かず前の画面へ戻ってしまう問題を修正しました。</li>
        <li>Androidで動画を再生したままホーム画面へ戻ると、音声が鳴り続ける問題を修正しました。</li>
        <li>メディアのZIP保存をやり直して失敗したときに、前に保存したZIPが消える問題を修正しました。Androidで選んだフォルダへ多数のファイルを保存するときの処理を速くし、保存中にほかのアプリでファイルが削除されても保存を続けられるようにしました。</li>
        <li>履歴が上限の件数に達すると新しく開いたスレッドが追加されない問題を修正し、古く閲覧した履歴から整理するようにしました。履歴の取り込み中に削除した項目が元に戻る問題も修正しました。</li>
        <li>としあき（仮）モードの画像NGの判定データを設定とは別に保存するようにしました。画像NGが多いと一部の設定が読み込まれない、設定のバックアップを復元できないことがある問題を修正しました。</li>
        <li>バックグラウンドの巡回・生存確認・自動保存に時間の上限を設け、キーワード巡回の間隔を調整しました。自動保存では前回保存した画像を再利用して通信を減らしました。Androidで起動直後のバックグラウンド更新が失敗することがある問題も修正しました。</li>
        <li>大きな応答を受信しながらサイズの上限を確かめるようにし、通信時のメモリ使用を抑えました。スレッド・ドロワー・抽出ポップアップ・画像編集などの表示の負荷を減らしました。</li>
        <li>iOSで大きな動画を動画編集に取り込むときに、メモリ使用量がファイルの大きさに応じて増える問題を修正しました。iOSで動画を開いている間の状態確認を減らしました。</li>
        <li>動画編集の一時ファイルを削除できなかったときにアプリが終了することがある問題と、画像の再読み込みで読み込み中の表示が残ることがある問題を修正しました。</li>
        <li>Wear OSで、文字盤を表示している間はスマホとの接続確認を止めるようにしました。</li>
    </ul>
    <h2>11.4</h2>
    <ul>
        <li>履歴をスレッドの閲覧順で表示するよう修正しました。通信の完了、再読み込み、バックグラウンド更新で順序が変わらず、短時間の開き直しも反映します。更新前の履歴は従来の順序を引き継ぎます。</li>
        <li>巡回で見つけたスレッドを閲覧履歴に自動追加せず、巡回結果として管理するようにしました。開いたスレッドは履歴に追加されます。</li>
        <li>設定に「巡回管理」を追加し、巡回結果から開くボタンにも歯車アイコンを付けました。保存済み結果の再読込と、通信する「今すぐ巡回」の違いを明示しました。</li>
        <li>巡回の開始手順、自動実行と通知の条件、標準機能ではにじろぐの起動が不要なこと、Androidの外部にじろぐ連携について説明を追加しました。</li>
        <li>ヘルプに単語検索を追加しました。折りたたまれた説明も検索でき、該当する項目と一致した語を表示します。ふたちゃモードの設定からも共通ヘルプを開けます。</li>
        <li>履歴の「タブ一覧」「巡回」、設定やダイアログなどの薄く読みにくい文字色を見直し、明るいテーマと暗いテーマの両方で読みやすくしました。</li>
    </ul>
    <h2>11.3</h2>
    <ul>
        <li>ふたちゃモードの新着レスの先頭に出る帯を、としあき（仮）モードと同じ「新着レス ○件」の表示に揃えました。通常表示・ツリー表示の両方に対応します。</li>
        <li>ふたちゃモードでも、としあき（仮）モードと共通のタブ一覧、お気に入り、一括終了と取り消し、巡回結果、キャッシュ検索を利用できるようにしました。</li>
        <li>カタログとスレッドに、更新前の表示へ戻す操作を追加しました。カタログでは消えたスレッドの表示やレス数による優先表示、スレッドでは自動スクロールや前後のスレッドへの移動も利用できます。</li>
        <li>ふたちゃモードに、全体・板・スレッドごとの詳細NG管理、似た画像のNG、NGレスの抽出、そうだね数・返信数のしきい値による抽出を追加しました。</li>
        <li>ふたちゃモードの投稿画面に、手書き、あぷ小へのアップロード、音声入力、回線・機種情報の挿入を追加しました。</li>
        <li>ふたちゃモードで、画像・動画を選択して保存できる画像一覧と、本文のみ・サムネイル付き・全メディア付きから選ぶスレッド保存を利用できるようにしました。</li>
        <li>ふたちゃモードの添付の長押しや画像プレビューから画像検索を開けるようにしました。落ちたスレッドの取得では、第三者アーカイブも利用します。</li>
        <li>追加した設定を両モードで共有し、ふたちゃモードの既存の設定分類へまとめました。画像サイズ・カタログ取得件数・列数・保存先の重複した設定入口を整理し、詳細な表示・操作・通信設定、バックアップ・復元も利用できるようにしました。</li>
        <li>ふたちゃモードで、登録済みの板の表示名を変更できるようにしました。</li>
    </ul>
    <h2>11.2</h2>
    <ul>
        <li>画像・動画の取得に失敗した際、使用中のデータの解放や一時ファイルの削除でもエラーが起きると、元の失敗内容が失われたり、後片付けが中断されたりする問題を修正しました。</li>
        <li>取得に失敗した一時ファイルを削除できない場合は、追加のダウンロードを開始しないようにしました。</li>
    </ul>
    <h2>11.1</h2>
    <ul>
        <li>プロンプト・AIラベルの表示、画像編集、動画編集を追加しました。それぞれ設定から個別に有効にでき、初期状態はすべてOFFです。</li>
        <li>画像・動画の生成情報の表示、詳細、コピーに対応しました。表示・生成情報の読取・保存で取得済みの原本を共有し、保存済みファイルからも生成情報を確認できます。</li>
        <li>スマホ内の画像・動画を選び、性器候補の自動検出、モザイク・黒塗り、輪郭の調整から新規保存まで行えるようにしました。動画では時間指定、前後追尾、編集中の再生、手修正後の再確認にも対応します。自動解析にはモデルの導入が必要です。</li>
        <li>iOSのWebM再生で、再生能力の確認、読み込みや再生の失敗表示を改善しました。端末や動画の形式によっては再生できない場合があります。MP4への自動変換は行いません。</li>
        <li>メディア機能の使い方と、使用するライブラリのライセンスを設定から読めるようにしました。</li>
        <li>ふたちゃモードの履歴でURLを板名に置き換え、余白を縮めました。長いタイトルや大きな文字を折り返し、ブラックテーマでも文字や操作が見やすくなるよう修正しました。</li>
    </ul>
    <h2>11.0</h2>
    <ul>
        <li>Android 16などで、戻るジェスチャー中の画面切り替えや画面を閉じるタイミングによってアプリが終了する問題を修正しました。</li>
        <li>Android版のとしあき（仮）モードで、ビルド条件によって起動時にアプリが終了する問題を修正しました。</li>
        <li>iOS版の両モードで、画像を選んでも添付されない、写真選択画面がキャンセルや下スワイプで閉じられない場合がある問題を修正しました。動画の選択処理も同様に見直しました。</li>
        <li>iOS版のとしあき（仮）モードで、画像の読み込み上限が正しく反映されず、圧縮できる画像も読み込めない問題を修正しました。「ファイル」から動画も選べるようにしました。</li>
        <li>HEIC／HEIF画像をJPEGへ、iOS版のMOV／M4V動画をMP4へ変換して添付できるようにしました。変換や添付の読み込みに失敗した場合はエラーを表示します。</li>
        <li>Android版で、提供元のサイズ情報が不正な添付ファイルを、途中で切れたまま読み込む問題を修正しました。</li>
        <li>ふたちゃモードのコンパクトヘッダーで、スレッド上部のバーも低くなるように修正しました。長い板名などを1行に収め、設定行全体をタップして切り替えられるようにしました。</li>
        <li>両モードの返信・引用する内容の選択画面に「全選択」「全解除」を追加しました。画面外の項目もまとめて選択・解除できます。</li>
        <li>両モードで、最下部へ移動するボタンを押すと、現在位置より先に新着がある場合は新着の先頭で一度止まり、もう一度押すと末尾へ移動するようにしました。ふたちゃモードにも「ここから新着」の区切りを追加しました。</li>
        <li>Android版で、初回の保存操作から保存先フォルダを選んで保存を続けられるようにしました。別の保存先を選ぶと、以前選んだフォルダへ保存できなくなる問題も修正しました。</li>
        <li>両モードの保存結果に保存先を表示し、保存した画像・動画を共有できるようにしました。本文HTMLや画像・動画の一部が保存できなかった場合も結果に表示します。端末内に保存済みの画像・動画を再保存できない問題も修正しました。</li>
        <li>としあき（仮）モードで、保存先の設定と保存一覧が一致しない問題を修正しました。現在の保存先にある、一覧に未登録の保存済みスレッドも読み込むようにしました。</li>
        <li>iOS版で、アプリ内の保存ファイルを「ファイル」アプリの「このiPhone内」から確認できるようにしました。</li>
    </ul>
    <h2>10.9</h2>
    <ul>
        <li>としあき（仮）モードで、更新履歴が繰り返し自動表示される問題を修正しました。アップデート後は一度だけ自動表示し、新規インストール時は自動表示しません。設定から手動で開くこともできます。</li>
        <li>保存済みHTMLを開く際の読み込み処理を見直し、画面が応答しなくなる原因となる処理を減らしました。iOS版では21MiBを超えるHTMLの読み込みをエラーとして扱います。</li>
        <li>としあき（仮）モードのカタログ・スレッドで、スクロール中の不要な再描画を減らしました。高速スクロールバーをOFFにしている場合の負荷も抑えました。</li>
        <li>としあき（仮）モードの設定・下書き・画像一覧・ツールバーなどで、読み書きの失敗によってアプリが終了する問題を修正しました。下書きを読み込めなかった場合に、空の内容で上書きしないようにしました。</li>
        <li>としあき（仮）モードで、投稿成功後に端末内の設定保存や下書き削除に失敗しても、投稿成功の通知まで進むよう修正しました。</li>
        <li>Android版のGIF・APNG・アニメーションWebPの読み込みを見直し、巨大な画像によるメモリ不足や長時間の処理を防ぐための上限を追加しました。容量や画像サイズが上限を超えるものは読み込みエラーになります。</li>
        <li>iOS版の添付画像圧縮で、大きな画像を先に縮小し、メモリ使用量を抑えるようにしました。圧縮後の画質や容量が以前と変わる場合があり、32MiBを超えるファイルは圧縮できません。</li>
        <li>iOS版のとしあき（仮）モードで、キャッシュが増えると設定や下書きを保存できなくなる問題を修正しました。保存上限に達すると「無制限」設定でも古い本文・カタログのキャッシュを整理します。整理された本文は、再取得するまでオフラインで読めなくなります。</li>
        <li>Android版で、端末内AIを利用できない場合の代替要約処理を見直し、画面操作を妨げにくくしました。</li>
    </ul>
    <h2>10.8</h2>
    <ul>
        <li>としあき（仮）モードの画面下部にあるタブ一覧で、白いタイトル文字が背景帯より上にずれる問題を修正しました。</li>
        <li>タブ一覧を長押しして上へドラッグした際、タブを閉じるまでに必要な移動距離を短くしました。</li>
        <li>iOS版のヘルプ・設定・更新履歴にあるストアボタンから、iOS版ふたちゃのApp Storeページを開くよう修正しました。</li>
    </ul>
    <h2>10.7</h2>
    <ul>
        <li>としあき（仮）モードでカラーテーマをブラックにした際、更新履歴の見出しや文字色設定によって本文が見えなくなる問題を修正しました。返信画面の選択済みsage・入力中の下線と、画像一覧の保存中表示が見えなくなる問題も修正しました。</li>
        <li>ふたちゃモードととしあき（仮）モードのスレ本文の下に、削除されたレスの件数を赤字で表示するようにしました。スレ主・投稿者本人・管理者による削除と隔離を種類別に表示し、判別できない分は「種類不明」として表示します。</li>
        <li>モードを切り替えた際にアプリアイコンが変わる仕組みを廃止しました。Android／iOSとも、選択したアイコンを両モードで共通して使用します。</li>
        <li>としあき（仮）モードのクイック返信・引用返信で、引用文の末尾に入る改行を2つから1つに変更しました。</li>
        <li>本人削除・管理者削除を含む削除・隔離の通知が赤字で表示されるよう修正しました。元の本文は通常の文字色を維持し、削除された本文を非表示にした場合も、削除の種類を正しく表示します。</li>
    </ul>
    <h2>10.6</h2>
    <ul>
        <li>Android版で、文字の長押し・ダブルタップによる選択中に編集した際や、端末のスマート選択処理に失敗した際にアプリが終了する問題を修正しました。スマート選択に失敗した場合も、通常の文字選択やコピーを続けられるようにしました。</li>
        <li>としあき（仮）モードの投稿画面で、回線情報の取得やあぷ小へのアップロードを待つ間に入力した本文が、処理完了時に古い内容で上書きされる問題を修正しました。</li>
        <li>としあき（仮）モードの投稿画面で、下書きの読み込みが遅れた場合に、入力済みの本文・名前・メール・題名・削除キーや変更した添付が上書きされないようにしました。空欄に戻す操作やリセット・破棄も維持します。</li>
        <li>Android／iOSの画面表示・通信・データ保存などに使用するライブラリを更新しました。一部にはalpha・beta・RC版を採用しています。</li>
    </ul>
    <h2>10.5</h2>
    <ul>
        <li>Android版で、通信の接続が切れた後に画像の表示まで長く待たされる問題を改善しました。</li>
        <li>画像の読み込みに失敗した際、同じ画像の再試行や別形式の画像の探索を必要以上に繰り返さないようにしました。</li>
        <li>以前の画像の読み込み失敗がキャッシュに残っていても、再取得して表示できるようにしました。正常に読み込めた画像は引き続き再利用します。</li>
        <li>画像を再読み込みした後、成功した画像をキャッシュへ保存し、画面に戻った際の不要な再通信を抑えるようにしました。</li>
        <li>としあき（仮）モードの画像ビューアーで、不要になった先読みを停止し、表示中の画像の読み込みを妨げにくくしました。</li>
        <li>iOS版で、画像の通信が途中で止まった際の待ち時間設定が正しく反映されるように修正しました。</li>
    </ul>
    <h2>10.4</h2>
    <ul>
        <li>としあき（仮）モードに、にじろぐ相当の内蔵巡回機能を追加しました。外部アプリを入れなくても、「巡回結果 → 巡回管理」から利用できます。</li>
        <li>巡回キーワードを板ごとに登録し、編集・削除・有効／無効の切り替え・並べ替えができるようになりました。カタログのタイトルは全角・半角や英字の大小を揃えて照合します。</li>
        <li>巡回結果を閲覧履歴とは別に保存し、一致キーワード・レス数・落ち状態を表示するようにしました。結果は7日間・最大500件を保持し、個別削除や全削除をしても閲覧履歴・保存済みスレッドは削除しません。</li>
        <li>手動巡回、自動巡回のON／OFF、Wi-Fi接続時のみの巡回、新着通知の設定を追加しました。通知の許可や端末の通知設定も開けます。自動巡回の実行時刻はOSの制御に依存します。</li>
        <li>板別の巡回キーワードを監視ワード／NGバックアップに含めるようにしました。Android版では、従来の外部にじろぐ連携も選択して利用できます。</li>
        <li>としあき（仮）モードの「落ちたスレを削除」で、操作時に生存確認を行うよう修正しました。通信失敗を落ちと誤判定せず、キャッシュ表示によって落ち判定が解除される問題も修正しました。</li>
        <li>画像ビューアーで、画像を読み込む直前や切り替え時に「画像が読み込めませんでした」が一瞬表示される問題を修正しました。実際の読み込み失敗は引き続き表示します。</li>
        <li>本文・引用に表示される不要な「[link]」ラベルを非表示にしました。リンク先は維持し、既存キャッシュの表示にも適用します。</li>
    </ul>
    <h2>10.3</h2>
    <ul>
        <li>Android版にGoogle Playのアプリ内アップデートを追加しました。更新が認識された当日から6日目まではアプリを使い続けながら更新でき、7日目以降またはGoogle Playの重要度が4以上の場合は更新を優先して案内します。</li>
        <li>Android版で、ダウンロード済みの更新を再起動して適用できるようにし、中断された必須アップデートも次回の起動時に再開するようにしました。</li>
        <li>iOS版でApp Storeの公開版と公開日を確認し、公開から6日目までは「後で」を選べる案内、7日目以降はApp Storeでの更新が必要な案内を表示するようにしました。</li>
        <li>「アップデート確認」をOFFにしている場合も、公開から7日以上、またはAndroidでGoogle Playの重要度が4以上の緊急更新は表示します。通常の更新案内は引き続き表示しません。</li>
        <li>これらの更新案内は、ふたちゃモードととしあき（仮）モードの両方で利用できます。</li>
    </ul>
    <h2>10.2</h2>
    <ul>
        <li>としあき（仮）モードで、あぷ／あぷ小のサムネイルを初期状態から表示し、設定に応じたスレ・画像一覧・ビューアーの表示とタップ動作を修正しました。</li>
        <li>サムネイルの見た目や縦横比を維持したまま、表示サイズを超える大きな画像の読み込みを抑え、画像表示時のメモリ使用量を改善しました。</li>
        <li>としあき（仮）モードのカタログ検索・並び替え・NG・監視ワード処理を改善し、更新や表示切り替え時の引っかかりと不要な再描画を抑えました。</li>
        <li>スレを開いた直後は画面表示を優先してから自動保存を開始するようにし、オフライン保存機能を維持しながら初期表示時の通信・保存処理の競合を減らしました。</li>
        <li>画像キャッシュを起動中に入れ替えないようにし、画像の再読み込みや一瞬の空白を抑えました。キャッシュを準備できなかった場合の診断と安全なフォールバックも改善しました。</li>
        <li>端末のメモリ状況に応じて画像処理の同時実行数を調整し、画像が多いカタログやスレでもメモリ不足になりにくくしました。</li>
        <li>Android版に起動・カタログ・スレの主要操作を対象とした最適化を追加し、アプリの起動と画面移動を改善しました。</li>
        <li>Android版の動画データを上限付きで再利用し、同じMP4／WebMを開き直した時の再通信と待ち時間を抑えました。</li>
        <li>iOS版では対応するMP4をAVPlayerで再生するようにし、WebMがある画面では現在表示しているサムネイルを優先して生成するよう改善しました。</li>
    </ul>
    <h2>10.1</h2>
    <ul>
        <li>過去ログ本文・引用のあぷ／あぷ小ファイル名に付く「[見る]」が、過去ログ側のHTML形式によっては残る問題を修正しました。</li>
        <li>新規取得、修正前から端末に残るキャッシュ、オフラインコピー、保存済みHTMLのどの表示方法でも「[見る]」を除去するよう修正しました。</li>
    </ul>
    <h2>10.0</h2>
    <ul>
        <li>新規取得だけでなく、修正前から端末に残る過去ログキャッシュでもファイル名末尾の「[見る]」を除去するよう修正しました。</li>
        <li>削除・隔離通知だけを赤くし、元の本文は通常色で表示するよう修正しました。</li>
        <li>横長のあぷ小サムネイルを正しい縦横比で表示するよう修正しました。</li>
    </ul>
    <h2>9.9</h2>
    <ul>
        <li>としあき（仮）モードの更新履歴を、Android／iOSとも読みやすい文字サイズと行間で表示するよう修正しました。</li>
        <li>としあき（仮）モードの画像一覧で、PNG/APNGの判定結果を再利用し、画面を開き直すたびに同じ画像へ確認通信が発生する問題を修正しました。</li>
        <li>書き込み制限時の1時間待ち案内を維持しつつ、待機後にCookieの削除と再発行が繰り返される問題を修正し、サーバーからの理由も表示するようにしました。</li>
        <li>ソースコードをGitHub（https://github.com/inqueuet/futacha）で再公開しました。</li>
    </ul>
    <h2>9.8</h2>
    <ul>
        <li>ふたちゃモードの板一覧一括追加を、取得先の入力なしで利用できるようにし、取得できる全板を正しく登録するよう修正しました。</li>
        <li>スレ内サムネイルの表示とキャッシュ再利用を改善し、あぷ小画像の表示が遅い問題や、読み込み時に表示位置が一瞬ずれる問題を修正しました。</li>
        <li>書き込み画面のメニューを適切な大きさへ修正しました。</li>
        <li>過去ログで同じサムネイルが二重に表示される問題と、引用内の画像から不要なサムネイルが作られる問題を修正しました。</li>
        <li>としあき（仮）モードで、削除レス件数をスレ先頭へ表示しないようにし、過去ログ本文・引用のあぷ小ファイル名末尾に付く「[見る]」を除去しました。</li>
    </ul>
    <h2>9.7</h2>
    <ul>
        <li>ふたちゃモードで、板一覧から複数の板をまとめて追加できるようになりました。</li>
        <li>iOSのとしあき（仮）モードで、スレからカタログ、カタログから板一覧へ画面内の戻るボタンで移動できるようになりました。開いていたタブも維持されます。</li>
        <li>Android／iOSのふたちゃモードの標準アイコンを、新しいデザインへ変更しました。</li>
    </ul>
    <h2>9.6</h2>
    <ul>
        <li>としあき（仮）モードの画像一覧で、画像・動画を複数選択し、ZIPまたはフォルダへ一括保存できるようになりました。</li>
        <li>スレの保存メニューから、画像と動画をまとめて「メディアのみ」で保存できるようになりました。</li>
        <li>一括保存の進捗表示とキャンセルに対応し、保存に失敗した項目だけを再試行できるようになりました。</li>
    </ul>
    <h2>9.5</h2>
    <ul>
        <li>外部からスレURLを開いてとしあき（仮）モードへ切り替えた時、確認画面が重なったり更新履歴に遮られたりせず、指定したスレを開けるようにしました。</li>
        <li>スレ内サムネイルの一時的な読み込み失敗を再試行し、読み込み中は空白ではなく進捗表示を出すようにしました。</li>
        <li>1000レス規模のスレでも画面外の画像を一度に取得せず、表示している範囲を優先するようにしました。</li>
        <li>ツールバーの並び替えが振動する問題と、画像ビューアーから戻るとスレが意図せず更新される問題を修正しました。</li>
        <li>ブラックテーマ、削除レス、カタログのレス数、タブ文字背景、投稿画面のメニューなど、としあき（仮）モードの表示を修正しました。</li>
        <li>読み上げ、iOSの画像添付、旧版の監視・NGワード移行、画像保存先、板一覧の保持を修正しました。</li>
        <li>投稿ヘッダーを参照アプリに近い大きさへ調整し、文字を大きくした端末でも上部バーが切れないようにしました。</li>
        <li>更新履歴が繰り返し表示される問題を修正し、本文と見出しの文字を読みやすくしました。</li>
    </ul>
    <h2>9.4</h2>
    <ul>
        <li>ライセンス表示を、アプリで実際に使用している内容に合わせて修正します。</li>
        <li>変更履歴の内容を、実際の更新内容に合わせて修正します。</li>
    </ul>
    <h2>9.3</h2>
    <ul>
        <li>画像ビューアーの「元レスへ移動」と「ギャラリーへ移動」が、それぞれ正しい場所を開くようになりました。</li>
        <li>ギャラリーから画像を開いて戻った時、見ていた画像の位置へ戻るようになりました。</li>
        <li>Androidの互換モード設定を開く時、認識できない外部ストレージがある端末でアプリが落ちる問題を修正しました。</li>
        <li>利用できないSDカード保存先は安全に「利用不可」と判定し、内部保存へ戻るようにしました。</li>
        <li>iOSのビルド設定とアプリで使用する部品を更新しました。</li>
    </ul>
    <h2>9.1</h2>
    <ul>
        <li>板一覧の標準アイコン、削除・並べ替えアイコン、動画サムネ、引用表示を参照アプリに近い見た目へ変更しました。</li>
        <li>画像が読み込めない時の再試行とエラー表示を改善しました。</li>
        <li>読み上げ、保存進捗、投稿待ち、Cookie、アップローダー失敗時の案内を分かりやすくしました。</li>
        <li>iOSで添付ファイルを選べなかった時のエラー処理を改善しました。</li>
    </ul>
    <h2>9.0</h2>
    <ul>
        <li>互換モードの板更新、板追加、監視、カタログNG、スレNG、画像NG、操作、保存、通信、背景更新、デザイン設定を大幅に拡充しました。</li>
        <li>互換モードに参照アプリのヘルプ、全更新履歴、ライセンス画面を追加しました。初回起動時や更新後にも表示されます。</li>
        <li>カタログ、ドロワー、投稿、スレ、画像ビューアーのアイコンを参照アプリに近いものへ変更しました。</li>
        <li>Google Lens、IQDB、SauceNAO、Yandexなどの画像検索を利用できるようにしました。</li>
        <li>保存したHTMLをアプリ内で開けるようにし、長い保存処理の進捗・中止・完了表示を改善しました。</li>
        <li>削除レス、隔離レス、GIF/APNG、動画のエラーやコーデック情報の表示を改善しました。</li>
    </ul>
    <h2>8.9</h2>
    <ul>
        <li>互換モードの自動スクロール、画像NG、タブを閉じる／元に戻す、削除レス表示、ツールバー、テーマを参照アプリに近づけました。</li>
        <li>画像の逆検索をアプリ内から利用できるようにしました。</li>
        <li>スレ内画像をZIPへまとめて保存できるようにしました。</li>
        <li>Android／iOSの主要操作を継続的に確認する仕組みを強化し、更新による機能の後戻りを減らしました。</li>
    </ul>
    <h2>8.8</h2>
    <ul>
        <li>初めて開くスレやキャッシュが空のスレが、手動更新を押さなくても読み込まれるようになりました。</li>
        <li>通信が途中で切れた場合に自動で再試行するようになりました。</li>
        <li>画像が多い大容量スレや、1000レス規模のスレでも画像・動画を読み込みやすくなりました。</li>
        <li>iOS版の案内表示をApp Store審査に適した内容へ調整しました。</li>
    </ul>
    <h2>8.6</h2>
    <ul>
        <li>としあき互換モードのAndroid／iOSアプリアイコンと色を変更しました。</li>
        <li>利用者向けの新機能追加はありません。</li>
    </ul>
    <h2>8.5</h2>
    <ul>
        <li>壊れた設定や保存データ、極端に大きなスレ、画像、履歴を扱った時にアプリが固まりにくくなりました。</li>
        <li>通信、Cookie、履歴、保存、設定バックアップ、ファイル操作の失敗から復旧しやすくなりました。</li>
        <li>大量の引用、長文読み上げ、過大な検索結果を安全に扱えるようになりました。</li>
        <li>互換モードの読み上げ、描画、メール欄プリセット、ポップアップ配色を修正しました。</li>
    </ul>
    <h2>8.4</h2>
    <ul>
        <li>iOSでも、としあき互換モードの板、カタログ、スレ、設定、履歴、アーカイブ報告を利用できるようになりました。</li>
        <li>iOS用の互換モードアイコンを追加しました。</li>
        <li>iOSで互換モードの設定と開いていた画面を保持できるようになりました。</li>
    </ul>
    <h2>8.2</h2>
    <ul>
        <li>互換モードのタブ移動プレビュー、返信マーカー、ドラッグ中のポップアップ位置を修正しました。</li>
        <li>起動時に一瞬だけ違う色が表示される問題を修正しました。</li>
        <li>更新確認を設定からON／OFFできるようにしました。初期設定はONです。</li>
    </ul>
    <h2>8.0</h2>
    <ul>
        <li>カタログのレス増加数を、直前に取得したカタログと比べて正しく表示するようにしました。</li>
        <li>最後のスレタブを閉じた時、元のカタログまたは板へ戻るようになりました。</li>
        <li>カタログタブを見た時だけ既読数を更新し、裏で追加したタブは未読のまま残すようにしました。</li>
    </ul>
    <h2>7.8</h2>
    <ul>
        <li>カタログの時刻表示と、手動更新時の「新着レスあり／なし」表示を修正しました。</li>
        <li>別の板タブを見た後でも、戻る操作で最初に開いた板へ戻るようにしました。</li>
        <li>監視ワードと板別抽出ワードの一致理由を正しく表示するようにしました。</li>
        <li>タブをドラッグしている時の表示位置を修正しました。</li>
    </ul>
    <h2>7.7</h2>
    <ul>
        <li>スレのドロワーをスワイプした時に、画面まで戻ってしまう問題を修正しました。</li>
        <li>履歴を削除した後、遅れて走った保存処理で履歴が復活する問題を修正しました。</li>
        <li>保存データの全削除中に古い保存処理が割り込まないようにしました。</li>
        <li>ギャラリーのサムネ表示方法を保存できるようになりました。</li>
    </ul>
    <h2>7.5</h2>
    <ul>
        <li>引っ張って更新する操作を途中で戻した時、誤って更新されないようにしました。</li>
        <li>ドロワーを開いている途中と開き終わった後で、背景の暗さが変わらないようにしました。</li>
        <li>更新通知を簡潔にし、アーカイブ報告の送信を安定させました。</li>
    </ul>
    <h2>7.2</h2>
    <ul>
        <li>互換モードのドロワーを閉じて開き直した時、直前に選んでいた場所を覚えるようになりました。</li>
        <li>モード切替後の設定、タブ、画像ビューアーの状態が不安定になる問題を修正しました。</li>
    </ul>
    <h2>7.1</h2>
    <ul>
        <li>端末のアプリ機能から板を開く、スレを開く、履歴を更新する操作を利用しやすくしました。</li>
        <li>互換画像ビューアーで拡大した時の位置ずれを修正しました。</li>
        <li>Google Lensへ画像ファイルを渡して検索できるようになりました。</li>
        <li>タブを閉じた直後に開き直す操作や、複数タブを続けて閉じる操作を安定させました。</li>
    </ul>
    <h2>6.7</h2>
    <ul>
        <li>互換カタログの列数・行数を参照アプリと同じ考え方で設定できるようにし、最大3000件を表示できるようにしました。</li>
        <li>カタログ更新後の位置、引っ張って更新、ページスワイプ、画像ビューアーの操作を改善しました。</li>
        <li>モード切替中にタブが消える問題と、通常履歴・互換履歴が重複する問題を修正しました。</li>
    </ul>
    <h2>6.6</h2>
    <ul>
        <li>通常モードと互換モードで板、履歴、保存スレッドを共有できるようになりました。</li>
        <li>共有時も画像・動画や「途中まで取得したスレ」の状態を保つようにしました。</li>
        <li>ドロワー、横ページ移動、引っ張って更新が競合しにくくなりました。</li>
        <li>スクロール位置の復元と、外部アップローダー画像の表示を改善しました。</li>
    </ul>
    <h2>6.3</h2>
    <ul>
        <li>通常モードと互換モードの切替、起動、板・スレ読み込みを安定させました。</li>
        <li>互換モードの画像表示とフォント選択を改善しました。</li>
        <li>応援購入、Watch通知、読み上げ状態、履歴ドロワーの連携を修正しました。</li>
        <li>画面を移動する直前のスクロール位置が失われにくくなりました。</li>
    </ul>
    <h2>6.2</h2>
    <ul>
        <li>互換モードの引用が、レス番号だけでなくNo.表記、ID、IP、画像・動画のファイル名からも正しく移動するようになりました。</li>
        <li>引用された再投稿画像を、元画像の投稿者と誤認しないようにしました。</li>
    </ul>
    <h2>6.1</h2>
    <ul>
        <li>互換モードの設定項目から戻った時、いきなり元画面へ閉じず、まず設定トップへ戻るようになりました。</li>
        <li>設定画面を直接開いた場合も正しい呼び出し元へ戻るようにしました。</li>
    </ul>
    <h2>6.0</h2>
    <ul>
        <li>画像・動画の種類判定を見直し、大文字拡張子、URL末尾に情報が付く形式、拡張子なしURLを正しく扱えるようにしました。</li>
        <li>画像を開こうとして誤って動画URLへ切り替わる問題を修正しました。</li>
        <li>動画プレビューが勝手に再生を始めないようにしました。</li>
    </ul>
    <h2>5.9</h2>
    <ul>
        <li>互換カタログのカードサイズ、画像の切り抜き、余白、背景色を参照アプリに近づけました。</li>
    </ul>
    <h2>5.8</h2>
    <ul>
        <li>互換モードに高速スクロールバー、音量キー操作、画面タップによるページ移動、独自フォントを追加しました。</li>
        <li>画像の見た目が近いものをNGにする機能と、背景での監視更新を追加しました。</li>
        <li>APNG、Animated WebP、WEBM、MP4、外部アップローダー、EXIF情報の表示を改善しました。</li>
        <li>消えたスレの過去ログ補完、キャッシュ検索、板更新、外部アーカイブ、画像検索、UPSアップロードを追加しました。</li>
        <li>旧アプリの設定とNGワードを移行できるようにしました。</li>
    </ul>
    <h2>5.6</h2>
    <ul>
        <li>通常UIと切り替えて使える「としあき互換モード」を追加しました。</li>
        <li>互換モード専用の板、カタログ、スレ、タブ、ドロワー、画像ビューアー、投稿、NG、検索、設定を追加しました。</li>
        <li>ツールバーの項目と順番を編集できるようにしました。</li>
        <li>消えたスレをアーカイブへ報告する機能と、互換モード専用アイコンを追加しました。</li>
    </ul>
    <h2>5.4</h2>
    <ul>
        <li>履歴を「自分の投稿あり」「スレが存続中」「板」「タイトル」で絞り込み・並び替えできるようになりました。</li>
        <li>スレ検索で該当レスが画面中央へ来るようにし、同じレス番号がある場合の移動も修正しました。</li>
        <li>画像ビューアーを何度も開くと失敗する問題を修正しました。</li>
        <li>不要な一時保存データを定期的に掃除するようにしました。</li>
    </ul>
    <h2>5.3</h2>
    <ul>
        <li>Android／iOSに、ストア経由でアプリを応援できる購入導線を追加しました。</li>
        <li>購入しても機能差が生じない「支援」であることを表示しました。</li>
    </ul>
    <h2>5.1</h2>
    <ul>
        <li>監視ワードに一致する新着スレを背景で見つけ、スマートフォンとWatchへ通知できるようになりました。</li>
        <li>全角・半角の違い、板ごとの監視ワード、通知済みスレを考慮するようにしました。</li>
        <li>画面上部・下部を含むテーマ色とスレ設定の見た目を改善しました。</li>
    </ul>
    <h2>5.0</h2>
    <ul>
        <li>カタログで一度に取得する件数を設定できるようになりました。</li>
        <li>表示中のモード以外も監視し、監視ワードに一致するスレを見落としにくくしました。</li>
        <li>不具合や重い処理を発見しやすくする計測をAndroid／iOSへ追加しました。</li>
    </ul>
    <h2>4.9</h2>
    <ul>
        <li>アプリ内の広告表示を削除しました。</li>
        <li>過去ログ検索で、すでに存在しない結果を表示しないようにしました。</li>
        <li>Watch非対応端末でWatch同期がエラーにならないようにしました。</li>
    </ul>
    <h2>4.7</h2>
    <ul>
        <li>履歴を選んでエクスポート／インポートできるようになりました。自動保存した本文や画像・動画も一緒に移せます。</li>
        <li>履歴が増えても保存や起動が重くなりにくいようにしました。</li>
        <li>画面の文字サイズ、画像サイズ、投稿フォームの文字サイズを設定できるようになりました。</li>
        <li>設定項目を折りたためるようにし、返信画像に「極小」を追加しました。</li>
        <li>更新通知にリリースノートを表示するようにしました。</li>
        <li>自動保存済みの画像・動画を再利用し、更新のたびに同じファイルを取り直さないようにしました。</li>
    </ul>
    <h2>4.6</h2>
    <ul>
        <li>inqueuetの過去ログを、URLまたはスレ番号から直接探せるようになりました。</li>
        <li>消えたスレを過去ログから開きやすくなりました。</li>
        <li>投稿失敗時に、待ち時間やCookieの作り直しが必要かを分かりやすく表示するようにしました。</li>
        <li>画像URLの拡張子が違う場合にも別候補を試し、画像を表示できる可能性を高めました。</li>
        <li>Watchの読み上げ状態表示を改善しました。</li>
    </ul>
    <h2>4.4</h2>
    <ul>
        <li>watchOS版のアイコンと起動設定を追加しました。</li>
        <li>入力欄の文字やカーソルが、画面更新時に不安定になる問題を修正しました。</li>
        <li>自動保存データの削除に失敗しても、削除した履歴が画面へ残らないようにしました。</li>
    </ul>
    <h2>4.2</h2>
    <ul>
        <li>パスワードでアプリをロックできるようになりました。</li>
        <li>パスワードを連続して間違えた場合の待ち時間を追加しました。</li>
        <li>読み上げ、履歴更新、保存、検索、AIフィルターが極端なデータで止まりにくくなりました。</li>
        <li>カタログのタイトル取得と、スレ末尾の埋め込み表示を改善しました。</li>
    </ul>
    <h2>4.1</h2>
    <ul>
        <li>Wear OS／watchOSアプリを追加しました。</li>
        <li>Watchから板やスレを開く、履歴を更新する、読み上げを開始・停止・移動する操作ができるようになりました。</li>
        <li>板、履歴、新着レス数、本文プレビュー、監視ワード、読み上げ状態をWatchで確認できるようになりました。</li>
    </ul>
    <h2>4.0</h2>
    <ul>
        <li>端末のアプリ機能やApple Intelligenceから、板表示、スレ表示、履歴更新を実行できるようになりました。</li>
        <li>端末内AIでスレを要約し、不要な投稿を自動で隠す機能を追加しました。</li>
        <li>Android 17へ対応しました。</li>
        <li>Android／iOSの画像・動画選択、保存、動画サムネ表示を改善しました。</li>
    </ul>
    <h2>3.9</h2>
    <ul>
        <li>スレ画像ギャラリーがシステムバーや画面下部と重なる問題を修正しました。</li>
        <li>利用者向けの新機能追加はありません。</li>
    </ul>
    <h2>3.8</h2>
    <ul>
        <li>スレ作成・返信に失敗した時、サーバーから返された理由や投稿待ち時間を表示するようになりました。</li>
        <li>Cookieが原因と思われる場合に、Cookieを作り直す案内を表示するようになりました。</li>
        <li>投稿後のカタログ更新と、カタログの絞り込みを安定させました。</li>
    </ul>
    <h2>3.7</h2>
    <ul>
        <li>ライト／ダークテーマの色を見直しました。</li>
        <li>板カード、投稿カード、設定、確認ダイアログの文字やボタンを読みやすくしました。</li>
        <li>利用者向けの新機能追加はありません。</li>
    </ul>
    <h2>3.6</h2>
    <ul>
        <li>カタログに題名がないスレでも、スレ本文から題名を取得して表示するようになりました。</li>
        <li>一度取得した題名を再利用し、同じスレへ何度もアクセスしないようにしました。</li>
    </ul>
    <h2>3.4</h2>
    <ul>
        <li>通常、クラシック、ミッドナイトのアプリアイコンを設定から切り替えられるようになりました。</li>
        <li>システム設定に合わせるテーマと、アイコンのプレビューを追加しました。</li>
        <li>引用のつながりを階層で読めるスレッドツリー表示を追加しました。</li>
    </ul>
    <h2>3.3</h2>
    <ul>
        <li>共通設定の表示と保存を安定させました。</li>
        <li>画像・動画プレビューを閉じて開き直した時の位置ずれを修正しました。</li>
        <li>横スワイプが縦スクロール中に誤作動しにくくなりました。</li>
    </ul>
    <h2>3.2</h2>
    <ul>
        <li>スレ内の画像と動画をまとめて見られる添付ギャラリーを追加しました。</li>
        <li>ギャラリーに画像／動画の種類とファイル名を表示するようにしました。</li>
        <li>画像・動画プレビューの前後移動を修正しました。</li>
        <li>画像または動画を1件だけ保存できるようになりました。</li>
    </ul>
    <h2>3.0</h2>
    <ul>
        <li>iOS版の起動・配布構成を整え、Androidと同じ共通画面を安定して利用できるようにしました。</li>
        <li>スレ末尾に埋め込まれた外部コンテンツを表示できるようになりました。</li>
        <li>iOSの画像・動画選択、動画サムネ、投稿時の案内を改善しました。</li>
        <li>投稿、そうだね、保存、履歴更新、スクロール位置保存の不具合を修正しました。</li>
    </ul>
    <h2>2.8</h2>
    <ul>
        <li>保存済みスレッドを一覧から開く、削除する導線を追加しました。</li>
        <li>画像・動画を1件だけ保存する操作を追加しました。</li>
        <li>履歴、自動保存、Cookie、投稿、URL、動画、背景更新の安定性を大きく改善しました。</li>
    </ul>
    <h2>2.7</h2>
    <ul>
        <li>本文内のふたばスレURLをタップした時、登録済みの板ならアプリ内で開くようになりました。</li>
        <li>未登録板やふたば以外のURLは、これまでどおり外部ブラウザで開きます。</li>
    </ul>
    <h2>2.6</h2>
    <ul>
        <li>iOSのWEBM再生と動画サイズ表示を安定させました。</li>
        <li>オフラインコピーの表示とスクロール位置復元を軽くしました。</li>
        <li>iOSの背景更新、保存先、ファイル操作、画像選択の失敗を減らしました。</li>
    </ul>
    <h2>2.5</h2>
    <ul>
        <li>画像・動画プレビューから、そのファイルだけを選んだ保存先へ保存できるようになりました。</li>
        <li>同じ画像を何度も保存しないようにし、保存失敗時の不要ファイルを自動で削除するようにしました。</li>
        <li>過去ログ検索、通信、Cookie、履歴更新を安定させました。</li>
    </ul>
    <h2>2.4</h2>
    <ul>
        <li>Androidのフォルダ選択とiOSのフォルダ選択を、スレ保存と自動保存で共通して使えるようになりました。</li>
        <li>保存中の同時操作による重複保存や索引破損を防ぐようにしました。</li>
        <li>投稿できない文字を安全な文字へ置き換え、文字化けや投稿失敗を減らしました。</li>
        <li>背景更新の二重起動と、保存先の権限切れを分かりやすく扱うようにしました。</li>
    </ul>
    <h2>2.3</h2>
    <ul>
        <li>大きな画像・動画を少しずつ保存し、保存中にメモリ不足になりにくくしました。</li>
        <li>保存済みスレのローカル画像・動画を、オフライン時に正しく表示するようにしました。</li>
        <li>通信、画像選択、背景更新が長時間止まった場合に中断・再試行できるようになりました。</li>
        <li>設定の読み込みに一時的に失敗しても、直前の設定で起動できるようになりました。</li>
    </ul>
    <h2>2.2</h2>
    <ul>
        <li>カタログ下部メニューの設定が壊れても、最低限の操作と設定画面を開けるようにしました。</li>
    </ul>
    <h2>2.1</h2>
    <ul>
        <li>Android／iOSの動画プレーヤーで、再生中・停止中・再生終了の表示が実際の状態と合うようになりました。</li>
        <li>動画再生中に不要な閉じる表示が出る問題を修正しました。</li>
    </ul>
    <h2>2.0</h2>
    <ul>
        <li>Androidの保存先をフォルダ選択へ統一し、一度許可した保存先を引き続き使えるようにしました。</li>
        <li>監視ワードに一致したスレの履歴追加を修正しました。</li>
        <li>保存済みスレの件数と使用容量を表示するようになりました。</li>
        <li>カタログ、履歴、Cookieの処理を軽くし、画面が固まりにくくなりました。</li>
    </ul>
    <h2>1.9</h2>
    <ul>
        <li>正式なiOSアプリを追加し、Androidと同じ板・カタログ・スレ・設定を利用できるようになりました。</li>
        <li>過去ログ検索で、結果の形式が変わった場合や情報が欠けた場合にも開きやすくしました。</li>
        <li>検索、画像表示、保存、背景更新の動作を軽くしました。</li>
        <li>Cookie、投稿、保存データ、HTML解析の安全性と安定性を改善しました。</li>
    </ul>
    <h2>1.8</h2>
    <ul>
        <li>大きなカタログを軽く読み込めるようにし、題名と画像URLの取得を改善しました。</li>
        <li>Cookieが原因で操作に失敗した場合、Cookieを作り直して1回だけ再試行するようになりました。</li>
        <li>背景更新時の本文・画像・動画の自動保存を安定させました。</li>
        <li>消えたスレでも保存済みコピーがある場合は残して表示するようにしました。</li>
    </ul>
    <h2>1.7</h2>
    <ul>
        <li>スレ作成・返信フォームの入力内容と削除キーを保持するようになりました。</li>
        <li>前回使った削除キーを次の投稿で再利用できるようになりました。</li>
        <li>Cookieが残っている場合の不要な準備通信を減らしました。</li>
        <li>隔離・削除されたレスの表示を修正しました。</li>
    </ul>
    <h2>1.6</h2>
    <ul>
        <li>板、カタログ、スレをふたば風の配色と見た目へ変更しました。</li>
        <li>カタログの列数と表示方法を保存するようにしました。</li>
    </ul>
    <h2>1.5</h2>
    <ul>
        <li>過去スレ検索のサービス側変更に対応し、検索できなくなっていた問題を修正しました。</li>
        <li>題名、サムネ、時刻が欠けた検索結果も開きやすくしました。</li>
    </ul>
    <h2>1.4</h2>
    <ul>
        <li>カタログから過去スレを検索できるようになりました。</li>
        <li>検索中、失敗、再試行、検索結果一覧を表示し、結果からスレを開けるようにしました。</li>
        <li>カタログ下部メニューの項目、順番、表示場所を編集して保存できるようになりました。</li>
    </ul>
    <h2>1.3</h2>
    <ul>
        <li>スレ下部の操作ボタンと設定メニューの項目・順番・表示場所を編集できるようになりました。</li>
        <li>編集したメニュー構成を次回起動後も保持するようになりました。</li>
        <li>返信コメント欄、添付を開く、引用などの投稿操作を改善しました。</li>
    </ul>
    <h2>1.2</h2>
    <ul>
        <li>Androidの背景更新方式を変更し、通知を出し続けずに定期更新するようになりました。</li>
        <li>優先して使うファイラーアプリを選べるようになりました。</li>
        <li>Android／iOSで保存先を選びやすくし、添付ファイルの選択方法を追加しました。</li>
        <li>画像キャッシュを設定から削除できるようになりました。</li>
    </ul>
    <h2>1.1</h2>
    <ul>
        <li>Android／iOSで、履歴を背景更新できるようになりました。</li>
        <li>背景更新時にスレ本文、画像、動画を自動保存するようになりました。</li>
        <li>Cookie管理画面とCookieの保存を追加しました。</li>
        <li>手動保存先の変更、保存済みスレ一覧、オフライン表示を改善しました。</li>
        <li>スクロール中の誤タップ、引用、画像・動画プレビュー、キャッシュの不具合を修正しました。</li>
        <li>プライバシーポリシーを追加し、カタログモードを保存するようにしました。</li>
    </ul>
    <h2>1.0</h2>
    <ul>
        <li>スワイプ操作が敏感すぎる問題を調整しました。</li>
        <li>引用プレビューを開いている間は、投稿の長押しメニューを表示しないようにしました。</li>
        <li>文字選択とタップ・長押し操作がぶつかる箇所を修正しました。</li>
        <li>保存したHTMLのリンクと、読み上げ終了時の動作を修正しました。</li>
    </ul>
    <h2>0.9</h2>
    <ul>
        <li>ランチャーアイコンを変更しました。</li>
        <li>読み上げ操作と件数表示を修正しました。</li>
        <li>本文中のURL、動画リンク、外部リンクをタップしやすくしました。</li>
        <li>引用、削除、ID、画像・動画の読み取りを改善しました。</li>
    </ul>
    <h2>0.6</h2>
    <ul>
        <li>読み上げ時に本文中のURLを読まないようにしました。</li>
        <li>クラッシュや重い処理を見つけやすくし、継続して修正できるようにしました。</li>
        <li>利用者向けの大きな機能追加はありません。</li>
    </ul>
    <h2>0.5</h2>
    <ul>
        <li>画像・動画プレビューを左右へスワイプして前後移動できるようになりました。</li>
        <li>手動保存と自動保存の保存場所を分けました。</li>
    </ul>
    <h2>0.4</h2>
    <ul>
        <li>板、カタログ、スレのメニュー、ダイアログ、下部バー、履歴の見た目と操作を改善しました。</li>
        <li>記号や特殊文字の文字化けを修正しました。</li>
        <li>スレ本文と引用の表示を改善しました。</li>
    </ul>
    <h2>0.3</h2>
    <ul>
        <li>スレ下部に、本文・名前・IDなどでレスを絞り込むフィルターと並び替えを追加しました。</li>
        <li>自分の投稿が分かるようになりました。</li>
        <li>スクロール位置を保存し、スレを開き直した時に戻れるようにしました。</li>
    </ul>
    <h2>0.1</h2>
    <ul>
        <li>Android／iOS共通の「ふたちゃ」アプリを新規作成しました。</li>
        <li>板管理、カタログ、スレ表示、履歴、スクロール位置保存、検索、引用プレビューを追加しました。</li>
        <li>スレ作成、返信、削除、そうだね、Cookie、Shift_JIS投稿に対応しました。</li>
        <li>NG、監視ワード、プライバシー表示、外部アプリ、読み上げ、表示切替を追加しました。</li>
        <li>画像・動画プレビュー、動画再生、画像キャッシュ、スレ本文・画像・動画の保存を追加しました。</li>
    </ul>
    </body>
    </html>
""".trimIndent()

internal data class FutachaChangeLogEntry(
    val version: String,
    val changes: List<String>
)

private val futachaChangeLogSectionRegex = Regex(
    pattern = """(?s)<h2>([^<]+)</h2>\s*<ul>(.*?)</ul>"""
)
private val futachaChangeLogItemRegex = Regex(
    pattern = """(?s)<li>(.*?)</li>"""
)

/**
 * The release-note source is also used by publication tooling as HTML, but the
 * in-app history is rendered with Compose so Android WebView scale/density
 * settings cannot shrink the text to physical-pixel size (#70).
 */
internal fun parseFutachaChangeLogEntries(html: String): List<FutachaChangeLogEntry> =
    futachaChangeLogSectionRegex.findAll(html).map { section ->
        FutachaChangeLogEntry(
            version = section.groupValues[1].trim(),
            changes = futachaChangeLogItemRegex.findAll(section.groupValues[2])
                .map { item -> item.groupValues[1].trim() }
                .toList()
        )
    }.filter { it.version.isNotEmpty() && it.changes.isNotEmpty() }
        .toList()

internal val FUTACHA_CHANGE_LOG_ENTRIES: List<FutachaChangeLogEntry> =
    parseFutachaChangeLogEntries(FUTACHA_CHANGE_LOG_HTML)

internal data class FutachaLicenseAsset(val id: String, val text: String, val resourcePath: String? = null)

/**
 * Notices for software used by Futacha itself.  This deliberately excludes
 * the reference application's dependency list (FFmpeg, Glide, Picasso, etc.).
 */
internal val FUTACHA_LICENSE_ASSETS: List<FutachaLicenseAsset> = listOf(
    FutachaLicenseAsset("onnxruntime-license", "ONNX Runtime 1.30.0 — MIT License", "files/licenses/onnxruntime-LICENSE.txt"),
    FutachaLicenseAsset("onnxruntime-notices", "ONNX Runtime — Third-party notices", "files/licenses/onnxruntime-ThirdPartyNotices.txt"),
    FutachaLicenseAsset("opencv-notices", "OpenCV 5.0.0 — Apache License 2.0 / Copyright / Third-party notices", "files/licenses/opencv-NOTICES.txt"),
    FutachaLicenseAsset(
        id = "futacha-open-source-notices",
        text = """ふたちゃが利用している主なオープンソースソフトウェア

この一覧は、としあき(仮)モードの参考元アプリではなく、ふたちゃ自身の実装と現在の直接依存関係に基づいています。

Kotlin / kotlinx.coroutines / kotlinx.serialization / kotlinx-datetime
Copyright JetBrains s.r.o. and respective project contributors
Apache License 2.0
https://github.com/JetBrains/kotlin
https://github.com/Kotlin

Compose Multiplatform
Copyright JetBrains s.r.o. and contributors
Apache License 2.0
https://github.com/JetBrains/compose-multiplatform

AndroidX（Jetpack Compose、Media3、WorkManager、DataStore、DocumentFile を含む）
Copyright The Android Open Source Project
Apache License 2.0
https://github.com/androidx/androidx

Material Components for Android / Material Icons
Copyright Google LLC and contributors
Apache License 2.0
https://github.com/material-components/material-components-android
https://github.com/google/material-design-icons

Ktor
Copyright JetBrains s.r.o. and contributors
Apache License 2.0
https://github.com/ktorio/ktor

Coil 2 / Coil 3
Copyright Coil Contributors
Apache License 2.0
https://github.com/coil-kt/coil

OkHttp / Okio
Copyright Square, Inc. and contributors
Apache License 2.0
https://github.com/square/okhttp
https://github.com/square/okio

APNG4Android（APNG / Animated WebP decoder）
Copyright Penfeizhou and contributors
Apache License 2.0
https://github.com/Penfeizhou/APNG4Android

Guava
Copyright Google LLC
Apache License 2.0
https://github.com/google/guava

Accompanist Drawable Painter
Copyright The Android Open Source Project
Apache License 2.0
https://github.com/google/accompanist

JSpecify
Copyright The JSpecify Authors
Apache License 2.0
https://github.com/jspecify/jspecify

各成果物に含まれる個別の著作権表示および NOTICE がある場合は、それらも適用されます。Apache License 2.0 の全文は次項に掲載します。"""
    ),
    FutachaLicenseAsset(
        id = "google-sdk-terms",
        text = """Google SDK（Android のみ）

ふたちゃの Android 版は Firebase Analytics、Firebase Crashlytics、Firebase Performance、ML Kit GenAI、Google Play Billing Library、および Google Play services for Wear OS を利用します。これらを上記 OSS と一括して Apache License 2.0 と表示することはせず、各 Google サービスおよび SDK の適用規約に従います。

Google APIs Terms of Service
https://developers.google.com/terms

Firebase Terms
https://firebase.google.com/terms

ML Kit Terms of Service
https://developers.google.com/ml-kit/terms

Google Play Terms of Service
https://play.google.com/about/play-terms/"""
    ),
    FutachaLicenseAsset(
        id = "apache-license-2.0",
        text = """Apache License
Version 2.0, January 2004
http://www.apache.org/licenses/

TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

1. Definitions.

"License" shall mean the terms and conditions for use, reproduction,
and distribution as defined by Sections 1 through 9 of this document.

"Licensor" shall mean the copyright owner or entity authorized by
the copyright owner that is granting the License.

"Legal Entity" shall mean the union of the acting entity and all
other entities that control, are controlled by, or are under common
control with that entity. For the purposes of this definition,
"control" means (i) the power, direct or indirect, to cause the
direction or management of such entity, whether by contract or
otherwise, or (ii) ownership of fifty percent (50%) or more of the
outstanding shares, or (iii) beneficial ownership of such entity.

"You" (or "Your") shall mean an individual or Legal Entity
exercising permissions granted by this License.

"Source" form shall mean the preferred form for making modifications,
including but not limited to software source code, documentation
source, and configuration files.

"Object" form shall mean any form resulting from mechanical
transformation or translation of a Source form, including but
not limited to compiled object code, generated documentation,
and conversions to other media types.

"Work" shall mean the work of authorship, whether in Source or
Object form, made available under the License, as indicated by a
copyright notice that is included in or attached to the work
(an example is provided in the Appendix below).

"Derivative Works" shall mean any work, whether in Source or Object
form, that is based on (or derived from) the Work and for which the
editorial revisions, annotations, elaborations, or other modifications
represent, as a whole, an original work of authorship. For the purposes
of this License, Derivative Works shall not include works that remain
separable from, or merely link (or bind by name) to the interfaces of,
the Work and Derivative Works thereof.

"Contribution" shall mean any work of authorship, including
the original version of the Work and any modifications or additions
to that Work or Derivative Works thereof, that is intentionally
submitted to Licensor for inclusion in the Work by the copyright owner
or by an individual or Legal Entity authorized to submit on behalf of
the copyright owner. For the purposes of this definition, "submitted"
means any form of electronic, verbal, or written communication sent
to the Licensor or its representatives, including but not limited to
communication on electronic mailing lists, source code control systems,
and issue tracking systems that are managed by, or on behalf of, the
Licensor for the purpose of discussing and improving the Work, but
excluding communication that is conspicuously marked or otherwise
designated in writing by the copyright owner as "Not a Contribution."

"Contributor" shall mean Licensor and any individual or Legal Entity
on behalf of whom a Contribution has been received by Licensor and
subsequently incorporated within the Work.

2. Grant of Copyright License. Subject to the terms and conditions of
this License, each Contributor hereby grants to You a perpetual,
worldwide, non-exclusive, no-charge, royalty-free, irrevocable
copyright license to reproduce, prepare Derivative Works of,
publicly display, publicly perform, sublicense, and distribute the
Work and such Derivative Works in Source or Object form.

3. Grant of Patent License. Subject to the terms and conditions of
this License, each Contributor hereby grants to You a perpetual,
worldwide, non-exclusive, no-charge, royalty-free, irrevocable
(except as stated in this section) patent license to make, have made,
use, offer to sell, sell, import, and otherwise transfer the Work,
where such license applies only to those patent claims licensable
by such Contributor that are necessarily infringed by their
Contribution(s) alone or by combination of their Contribution(s)
with the Work to which such Contribution(s) was submitted. If You
institute patent litigation against any entity (including a
cross-claim or counterclaim in a lawsuit) alleging that the Work
or a Contribution incorporated within the Work constitutes direct
or contributory patent infringement, then any patent licenses
granted to You under this License for that Work shall terminate
as of the date such litigation is filed.

4. Redistribution. You may reproduce and distribute copies of the
Work or Derivative Works thereof in any medium, with or without
modifications, and in Source or Object form, provided that You
meet the following conditions:

(a) You must give any other recipients of the Work or
Derivative Works a copy of this License; and

(b) You must cause any modified files to carry prominent notices
stating that You changed the files; and

(c) You must retain, in the Source form of any Derivative Works
that You distribute, all copyright, patent, trademark, and
attribution notices from the Source form of the Work,
excluding those notices that do not pertain to any part of
the Derivative Works; and

(d) If the Work includes a "NOTICE" text file as part of its
distribution, then any Derivative Works that You distribute must
include a readable copy of the attribution notices contained
within such NOTICE file, excluding those notices that do not
pertain to any part of the Derivative Works, in at least one
of the following places: within a NOTICE text file distributed
as part of the Derivative Works; within the Source form or
documentation, if provided along with the Derivative Works; or,
within a display generated by the Derivative Works, if and
wherever such third-party notices normally appear. The contents
of the NOTICE file are for informational purposes only and
do not modify the License. You may add Your own attribution
notices within Derivative Works that You distribute, alongside
or as an addendum to the NOTICE text from the Work, provided
that such additional attribution notices cannot be construed
as modifying the License.

You may add Your own copyright statement to Your modifications and
may provide additional or different license terms and conditions
for use, reproduction, or distribution of Your modifications, or
for any such Derivative Works as a whole, provided Your use,
reproduction, and distribution of the Work otherwise complies with
the conditions stated in this License.

5. Submission of Contributions. Unless You explicitly state otherwise,
any Contribution intentionally submitted for inclusion in the Work
by You to the Licensor shall be under the terms and conditions of
this License, without any additional terms or conditions.
Notwithstanding the above, nothing herein shall supersede or modify
the terms of any separate license agreement you may have executed
with Licensor regarding such Contributions.

6. Trademarks. This License does not grant permission to use the trade
names, trademarks, service marks, or product names of the Licensor,
except as required for reasonable and customary use in describing the
origin of the Work and reproducing the content of the NOTICE file.

7. Disclaimer of Warranty. Unless required by applicable law or
agreed to in writing, Licensor provides the Work (and each
Contributor provides its Contributions) on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
implied, including, without limitation, any warranties or conditions
of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
PARTICULAR PURPOSE. You are solely responsible for determining the
appropriateness of using or redistributing the Work and assume any
risks associated with Your exercise of permissions under this License.

8. Limitation of Liability. In no event and under no legal theory,
whether in tort (including negligence), contract, or otherwise,
unless required by applicable law (such as deliberate and grossly
negligent acts) or agreed to in writing, shall any Contributor be
liable to You for damages, including any direct, indirect, special,
incidental, or consequential damages of any character arising as a
result of this License or out of the use or inability to use the
Work (including but not limited to damages for loss of goodwill,
work stoppage, computer failure or malfunction, or any and all
other commercial damages or losses), even if such Contributor
has been advised of the possibility of such damages.

9. Accepting Warranty or Additional Liability. While redistributing
the Work or Derivative Works thereof, You may choose to offer,
and charge a fee for, acceptance of support, warranty, indemnity,
or other liability obligations and/or rights consistent with this
License. However, in accepting such obligations, You may act only
on Your own behalf and on Your sole responsibility, not on behalf
of any other Contributor, and only if You agree to indemnify,
defend, and hold each Contributor harmless for any liability
incurred by, or claims asserted against, such Contributor by reason
of your accepting any such warranty or additional liability.

END OF TERMS AND CONDITIONS

APPENDIX: How to apply the Apache License to your work.

To apply the Apache License to your work, attach the following
boilerplate notice, with the fields enclosed by brackets "[]"
replaced with your own identifying information. (Don't include
the brackets!) The text should be enclosed in the appropriate
comment syntax for the file format. We also recommend that a
file or class name and description of purpose be included on the
same "printed page" as the copyright notice for easier
identification within third-party archives.

Copyright [yyyy] [name of copyright owner]

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License."""
    )
)
