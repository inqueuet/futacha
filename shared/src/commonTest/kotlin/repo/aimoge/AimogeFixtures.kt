package com.valoser.futacha.shared.repo.aimoge

/**
 * あいもげ responses for tests (AM-B0, docs/aimoge-support-plan-2026-11-10.md): public reads taken on 2026-11-10, cut
 * down, plus synthesized cases the samples lacked — in [THREAD] post seq 3 has a displayId, seq 4 a null
 * postSequenceNo, seq 5 is shadowed, seq 6 has broken private-use characters; [THREAD_CLOSED] is closed; the three
 * error shapes are synthesized or a real 404 body. No installation id, auth state, verifier, credential or delete key.
 */
internal object AimogeFixtures {
    const val BOARDS: String = """{
 "boards": [
  {
   "slug": "ai2",
   "boardName": "二次元AI裏",
   "allowR18ThreadCreation": true,
   "defaultDisplayName": null,
   "createdAt": "2026-07-26T10:37:01.268Z",
   "presentation": {
    "defaultVisible": true,
    "defaultEnabled": true,
    "imageUrl": "https://nijiurachan.net/assets/icon_board-ai2.webp"
   }
  },
  {
   "slug": "aimg",
   "boardName": "二次元裏",
   "allowR18ThreadCreation": true,
   "defaultDisplayName": null,
   "createdAt": "2026-08-07T20:07:17.390Z",
   "presentation": {
    "defaultVisible": true,
    "defaultEnabled": true,
    "imageUrl": "https://nijiurachan.net/assets/icon_board-aimg.webp"
   }
  },
  {
   "slug": "bl",
   "boardName": "二次元腐",
   "allowR18ThreadCreation": true,
   "defaultDisplayName": null,
   "createdAt": "2026-10-13T04:41:35.430Z",
   "presentation": {
    "defaultVisible": true,
    "defaultEnabled": false,
    "imageUrl": null
   }
  }
 ]
}"""

    const val CLIENT_POLICY_AIMG: String = """{
 "boardKey": "aimg",
 "allowedMimeTypes": [
  "image/jpeg",
  "image/png",
  "image/gif",
  "image/webp",
  "image/heic",
  "video/mp4",
  "video/quicktime",
  "video/webm"
 ],
 "maxFileSizeBytes": 20971520,
 "maxBodyLength": 4000,
 "maxDeleteKeyLength": 64,
 "threadsPerHour": 60,
 "threadCreationCooldownSeconds": 60,
 "replyCreationCooldownSeconds": 10,
 "threadReplyCreationCooldownSeconds": 5
}"""

    const val CATALOG: String = """{
 "sort": "bump",
 "threads": [
  {
   "id": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "opPost": {
    "id": "01a1ab6e-57e8-755a-8c24-61da88f0bd4d",
    "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
    "seq": 0,
    "postSequenceNo": 100493594,
    "status": "public",
    "body": "museスレ\nもちもち",
    "createdAt": "2026-11-05T11:18:17.867Z",
    "attachment": {
     "id": "01a1ab6e-2875-7389-9e96-7c5b2aff272c",
     "kind": "image",
     "mime": "image/webp",
     "width": 1024,
     "height": 1024,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00077338ad8.webp",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00077338ad8.webp",
     "ngHash": "_-_Pg4HBw_8",
     "isOekaki": false
    },
    "attachments": [
     {
      "id": "01a1ab6e-2875-7389-9e96-7c5b2aff272c",
      "kind": "image",
      "mime": "image/webp",
      "width": 1024,
      "height": 1024,
      "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00077338ad8.webp",
      "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00077338ad8.webp",
      "ngHash": "_-_Pg4HBw_8",
      "isOekaki": false
     }
    ],
    "sodaneCount": 12,
    "superSoudaneCount": 0,
    "displayId": null,
    "displayIdSource": null,
    "sage": false,
    "showThreadOwner": false,
    "poll": null
   },
   "replyCount": 639,
   "createdAt": "2026-11-05T11:18:17.867Z",
   "bumpedAt": "2026-11-10T10:41:30.299Z",
   "expiresAt": "2026-11-13T15:18:17.867Z",
   "isPermanent": false,
   "isSage": false,
   "forceDisplayId": false,
   "allowImageReplies": true,
   "threadNgWords": [],
   "threadRelay": null,
   "closedAt": null,
   "tags": [
    {
     "name": "技術・ガジェット",
     "kind": "fixed",
     "source": "ai"
    },
    {
     "name": "R18",
     "kind": "fixed",
     "source": "op"
    }
   ]
  },
  {
   "id": "01a1c1c0-d9bb-769e-80b4-cb23364ef732",
   "opPost": {
    "id": "01a1c1c0-d9bb-769d-8754-6787e7af7ab7",
    "threadId": "01a1c1c0-d9bb-769e-80b4-cb23364ef732",
    "seq": 0,
    "postSequenceNo": 100530908,
    "status": "public",
    "body": "あれ？「」くん…２限は教室移動だよ。忘れ物？\nえ？深夜？何言ってるの…深夜に太陽は出てないよ！\n彼女は窓の外を指さしている。\nもう…先生きちゃうよ！のんびりしてないで早く忘れ物取りに行かないとっ",
    "createdAt": "2026-11-09T19:20:03.810Z",
    "attachment": {
     "id": "01a1c1c0-bbc8-715f-b49c-3c80c7c08273",
     "kind": "image",
     "mime": "image/png",
     "width": 1216,
     "height": 832,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00083321aBC.png",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00083321aBC.webp",
     "ngHash": "j4-fn4Dk_Bw",
     "isOekaki": false
    },
    "attachments": [
     {
      "id": "01a1c1c0-bbc8-715f-b49c-3c80c7c08273",
      "kind": "image",
      "mime": "image/png",
      "width": 1216,
      "height": 832,
      "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00083321aBC.png",
      "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00083321aBC.webp",
      "ngHash": "j4-fn4Dk_Bw",
      "isOekaki": false
     }
    ],
    "sodaneCount": 8,
    "superSoudaneCount": 2,
    "displayId": null,
    "displayIdSource": null,
    "sage": false,
    "showThreadOwner": false,
    "poll": null
   },
   "replyCount": 166,
   "createdAt": "2026-11-09T19:20:03.810Z",
   "bumpedAt": "2026-11-10T10:39:29.124Z",
   "expiresAt": "2026-11-19T10:00:03.810Z",
   "isPermanent": false,
   "isSage": false,
   "forceDisplayId": false,
   "allowImageReplies": true,
   "threadNgWords": [],
   "threadRelay": null,
   "closedAt": null,
   "tags": [
    {
     "name": "創作・作品投稿",
     "kind": "fixed",
     "source": "ai"
    },
    {
     "name": "R18",
     "kind": "fixed",
     "source": "op"
    }
   ]
  },
  {
   "id": "01a1c4f1-3426-7234-91d2-bed846bd212d",
   "opPost": {
    "id": "01a1c4f1-3426-7233-89fa-0d585e44caf3",
    "threadId": "01a1c4f1-3426-7234-91d2-bed846bd212d",
    "seq": 0,
    "postSequenceNo": 100533977,
    "status": "public",
    "body": "関係性皆無だけど好きな2人組を貼って満足するスレ\n自由に貼っていいよ",
    "createdAt": "2026-11-10T10:11:44.386Z",
    "attachment": {
     "id": "01a1c4f1-0918-75c4-8eb9-e5043f111565",
     "kind": "image",
     "mime": "image/png",
     "width": 1024,
     "height": 1024,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00083807ano.png",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00083807ano.webp",
     "ngHash": "iaVnDw8PDgs",
     "isOekaki": false
    },
    "attachments": [
     {
      "id": "01a1c4f1-0918-75c4-8eb9-e5043f111565",
      "kind": "image",
      "mime": "image/png",
      "width": 1024,
      "height": 1024,
      "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00083807ano.png",
      "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00083807ano.webp",
      "ngHash": "iaVnDw8PDgs",
      "isOekaki": false
     }
    ],
    "sodaneCount": 3,
    "superSoudaneCount": 0,
    "displayId": null,
    "displayIdSource": null,
    "sage": false,
    "showThreadOwner": false,
    "poll": null
   },
   "replyCount": 7,
   "createdAt": "2026-11-10T10:11:44.386Z",
   "bumpedAt": "2026-11-10T10:39:01.556Z",
   "expiresAt": "2026-11-15T17:11:44.386Z",
   "isPermanent": false,
   "isSage": false,
   "forceDisplayId": false,
   "allowImageReplies": true,
   "threadNgWords": [],
   "threadRelay": null,
   "closedAt": null,
   "tags": [
    {
     "name": "創作・作品投稿",
     "kind": "fixed",
     "source": "ai"
    },
    {
     "name": "R18",
     "kind": "fixed",
     "source": "op"
    }
   ]
  },
  {
   "id": "01a1b10e-087f-7407-86e0-8f13799ca2d2",
   "opPost": {
    "id": "01a1b10e-087f-7406-ae38-2d4a8092e60b",
    "threadId": "01a1b10e-087f-7407-86e0-8f13799ca2d2",
    "seq": 0,
    "postSequenceNo": 100503824,
    "status": "public",
    "body": "シャニマスえっちなお仕事スレ\n\nアイドルが性的な要求に拒否権が無い(但し283プロのアイドル達は普通の羞恥心あり)な世界\nお仕事の撮影やファンとの触れ合いから個人的な要求までなんでもあり\n……みたいな都合のいい世界観を前提にしてもしなくてもいいです\nイラスト・文章による妄想や感想等好き勝手に書き込んで下さい\nリクエストもOKです\n※スカとふたなりとTSはNGで",
    "createdAt": "2026-11-06T13:30:49.376Z",
    "attachment": {
     "id": "01a1b10d-e387-74f2-ac4e-171c8f7ba08b",
     "kind": "image",
     "mime": "image/png",
     "width": 720,
     "height": 1280,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00078905aI_.png",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00078905aI_.webp",
     "ngHash": "T4kfej9_AAA",
     "isOekaki": false
    },
    "attachments": [
     {
      "id": "01a1b10d-e387-74f2-ac4e-171c8f7ba08b",
      "kind": "image",
      "mime": "image/png",
      "width": 720,
      "height": 1280,
      "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00078905aI_.png",
      "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00078905aI_.webp",
      "ngHash": "T4kfej9_AAA",
      "isOekaki": false
     }
    ],
    "sodaneCount": 5,
    "superSoudaneCount": 0,
    "displayId": null,
    "displayIdSource": null,
    "sage": false,
    "showThreadOwner": false,
    "poll": null
   },
   "replyCount": 25,
   "createdAt": "2026-11-06T13:30:49.376Z",
   "bumpedAt": "2026-11-10T10:38:53.184Z",
   "expiresAt": "2026-11-14T15:10:49.376Z",
   "isPermanent": false,
   "isSage": false,
   "forceDisplayId": false,
   "allowImageReplies": true,
   "threadNgWords": [],
   "threadRelay": null,
   "closedAt": null,
   "tags": [
    {
     "name": "創作・作品投稿",
     "kind": "fixed",
     "source": "ai"
    },
    {
     "name": "R18",
     "kind": "fixed",
     "source": "op"
    }
   ]
  },
  {
   "id": "01a1be83-d660-7212-8eb1-6d0003670565",
   "opPost": {
    "id": "01a1be83-d660-7211-b80b-24b574155478",
    "threadId": "01a1be83-d660-7212-8eb1-6d0003670565",
    "seq": 0,
    "postSequenceNo": 100525193,
    "status": "public",
    "body": "出力したメガネキャラ貼って\n非エロでもエロでも\nなんなら男でも人外でも",
    "createdAt": "2026-11-09T04:14:33.626Z",
    "attachment": {
     "id": "01a1be83-aee4-721f-9080-8ba45319db0e",
     "kind": "image",
     "mime": "image/jpeg",
     "width": 2000,
     "height": 2000,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00082423aE1.jpg",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00082423aE1.webp",
     "ngHash": "3ffAgPn5QAA",
     "isOekaki": false
    },
    "attachments": [
     {
      "id": "01a1be83-aee4-721f-9080-8ba45319db0e",
      "kind": "image",
      "mime": "image/jpeg",
      "width": 2000,
      "height": 2000,
      "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00082423aE1.jpg",
      "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00082423aE1.webp",
      "ngHash": "3ffAgPn5QAA",
      "isOekaki": false
     }
    ],
    "sodaneCount": 10,
    "superSoudaneCount": 1,
    "displayId": null,
    "displayIdSource": null,
    "sage": false,
    "showThreadOwner": false,
    "poll": null
   },
   "replyCount": 61,
   "createdAt": "2026-11-09T04:14:33.626Z",
   "bumpedAt": "2026-11-10T10:34:44.331Z",
   "expiresAt": "2026-11-10T19:34:33.626Z",
   "isPermanent": false,
   "isSage": false,
   "forceDisplayId": false,
   "allowImageReplies": true,
   "threadNgWords": [],
   "threadRelay": null,
   "closedAt": null,
   "tags": [
    {
     "name": "創作・作品投稿",
     "kind": "fixed",
     "source": "ai"
    },
    {
     "name": "R18",
     "kind": "fixed",
     "source": "op"
    }
   ]
  }
 ],
 "pagination": {
  "page": 1,
  "limit": 50,
  "hasNextPage": false
 }
}"""

    const val THREAD: String = """{
 "aiSummary": {
  "text": "AIサービス「Muse」の利用体験や登録方法について議論するスレッドです。登録にはクレジットカードやSNS連携による年齢認証が必要でハードルが高い一方、招待コードでトークンを得られる仕組みがあるようです。ユーザーはMuseに任せてアプリやドキュメントを作成する楽しさを報告し、ブラウザ操作やゲーム戦略思考など高度な機能への期待も語られています。また、生成小説の比率について感想を述べる投稿や、課金後の快適さに関する質問も見られます。",
  "updatedAt": "2026-11-10T10:03:28.361Z",
  "sourcePostSeqs": [
   0,
   1,
   2,
   3,
   4,
   5,
   6,
   7,
   8,
   9,
   10,
   11,
   319,
   630,
   633
  ]
 },
 "aiTitle": {
  "text": "AIサービスMuseの登録方法・招待コード・利用体験",
  "updatedAt": "2026-11-10T10:03:28.361Z",
  "sourcePostSeqs": [
   0,
   1,
   2,
   3,
   4,
   5,
   6,
   7,
   8,
   9,
   10,
   11,
   319,
   630,
   633
  ]
 },
 "id": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
 "replyCount": 11,
 "createdAt": "2026-11-05T11:18:17.867Z",
 "bumpedAt": "2026-11-10T10:41:30.299Z",
 "expiresAt": "2026-11-13T15:18:17.867Z",
 "isPermanent": false,
 "isSage": false,
 "forceDisplayId": false,
 "allowImageReplies": true,
 "threadNgWords": [],
 "threadRelay": null,
 "closedAt": null,
 "canShowThreadOwner": false,
 "tags": [
  {
   "name": "技術・ガジェット",
   "kind": "fixed",
   "source": "ai"
  },
  {
   "name": "R18",
   "kind": "fixed",
   "source": "op"
  }
 ],
 "posts": [
  {
   "id": "01a1ab6e-57e8-755a-8c24-61da88f0bd4d",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 0,
   "postSequenceNo": 100493594,
   "status": "public",
   "body": "museスレ\nもちもち",
   "createdAt": "2026-11-05T11:18:17.867Z",
   "attachment": {
    "id": "01a1ab6e-2875-7389-9e96-7c5b2aff272c",
    "kind": "image",
    "mime": "image/webp",
    "width": 1024,
    "height": 1024,
    "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00077338ad8.webp",
    "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00077338ad8.webp",
    "ngHash": "_-_Pg4HBw_8",
    "isOekaki": false
   },
   "attachments": [
    {
     "id": "01a1ab6e-2875-7389-9e96-7c5b2aff272c",
     "kind": "image",
     "mime": "image/webp",
     "width": 1024,
     "height": 1024,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00077338ad8.webp",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00077338ad8.webp",
     "ngHash": "_-_Pg4HBw_8",
     "isOekaki": false
    }
   ],
   "sodaneCount": 12,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  },
  {
   "id": "01a1ab71-162f-71c1-92f2-30ad27936212",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 1,
   "postSequenceNo": 100493641,
   "status": "public",
   "body": "おつ\nいつの間にか前のが落ちてたのね",
   "createdAt": "2026-11-05T11:21:17.654Z",
   "attachment": null,
   "attachments": [],
   "sodaneCount": 0,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  },
  {
   "id": "01a1ab72-a5ec-7622-979d-fc7ea832cb6e",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 2,
   "postSequenceNo": 100493658,
   "status": "public",
   "body": "スレ立て感謝\nデフォもちもちくん可愛いね…",
   "createdAt": "2026-11-05T11:22:59.984Z",
   "attachment": null,
   "attachments": [],
   "sodaneCount": 6,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  },
  {
   "id": "01a1ab76-6174-7001-a2f1-a08059411607",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 3,
   "postSequenceNo": 100493690,
   "status": "public",
   "body": "だんだんチャHがマンネリ化してきたけど登場人物を増やせばよかったんだ！ってなって楽しい\nジュニアアイドル上がりの子を作ったけど未成年時代のエロ仕事は禁止です！って言われたので18歳でアイドルになってそこから1年で仕事いっぱいこなしたのでまだ18歳ですってのが出来上がった",
   "createdAt": "2026-11-05T11:27:04.599Z",
   "attachment": null,
   "attachments": [],
   "sodaneCount": 1,
   "superSoudaneCount": 0,
   "displayId": "Ab3dE9xQ",
   "displayIdSource": "thread",
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  },
  {
   "id": "01a1ab76-6174-7002-a4c6-1dd7531b168c",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 4,
   "postSequenceNo": null,
   "status": "public",
   "body": "無料でお試しって出来るの？",
   "createdAt": "2026-11-05T11:27:06.498Z",
   "attachment": null,
   "attachments": [],
   "sodaneCount": 0,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  },
  {
   "id": "01a1ab77-7563-77ed-bfef-d170ee8a4bcd",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 5,
   "postSequenceNo": 100493708,
   "status": "shadowed",
   "body": ">無料でお試しって出来るの？\n週制限までなら\nあとは皆隙あらば招待コードはっつけて10億もらおうとしてる",
   "createdAt": "2026-11-05T11:28:15.236Z",
   "attachment": null,
   "attachments": [],
   "sodaneCount": 0,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  },
  {
   "id": "01a1ab79-5f95-7071-bc4f-9e958eb38946",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 6,
   "postSequenceNo": 100493736,
   "status": "public",
   "body": "私用領域の崩れここまで",
   "createdAt": "2026-11-05T11:30:20.727Z",
   "attachment": null,
   "attachments": [],
   "sodaneCount": 1,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  },
  {
   "id": "01a1ab7a-77e6-7752-bebd-bc7b92d5f03b",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 7,
   "postSequenceNo": 100493749,
   "status": "public",
   "body": ">>無料でお試しって出来るの？（No.100493693へ移動）\n>週制限までなら\n>あとは皆隙あらば招待コードはっつけて10億もらおうとしてる\nなるほど\nじゃあ登録だけしてみようかな\nなんか諸伝コードってのあるなら得ならそっから入るよ",
   "createdAt": "2026-11-05T11:31:34.506Z",
   "attachment": null,
   "attachments": [],
   "sodaneCount": 0,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  },
  {
   "id": "01a1ab7a-eb89-7196-9c36-7b9c9873cb09",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 8,
   "postSequenceNo": 100493757,
   "status": "public",
   "body": "みんなのアーティファクト見るの好きだから軽率に自慢してほしい\n俺のもそんな立派なもんじゃないけどほとんどmuse任せで作れて楽しいよ　1週間トークンは粉微塵になったよ\nコード　H0RBBB",
   "createdAt": "2026-11-05T11:32:02.107Z",
   "attachment": {
    "id": "01a1ab7a-b64b-7331-aec8-2369be3ec4ee",
    "kind": "image",
    "mime": "image/jpeg",
    "width": 1080,
    "height": 1913,
    "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00077369aaA.jpg",
    "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00077369aaA.webp",
    "ngHash": "YGB_fwAUHBw",
    "isOekaki": false
   },
   "attachments": [
    {
     "id": "01a1ab7a-b64b-7331-aec8-2369be3ec4ee",
     "kind": "image",
     "mime": "image/jpeg",
     "width": 1080,
     "height": 1913,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00077369aaA.jpg",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00077369aaA.webp",
     "ngHash": "YGB_fwAUHBw",
     "isOekaki": false
    },
    {
     "id": "01a1ab7a-b68f-76ef-b6aa-c3521c34c13b",
     "kind": "image",
     "mime": "image/jpeg",
     "width": 1063,
     "height": 1856,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00077369b2o.jpg",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00077369b2o.webp",
     "ngHash": "eXl_d1RgeH8",
     "isOekaki": false
    },
    {
     "id": "01a1ab7a-b602-70fe-8874-5abcd884f24b",
     "kind": "image",
     "mime": "image/jpeg",
     "width": 1080,
     "height": 1879,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00077369czz.jpg",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00077369czz.webp",
     "ngHash": "AAsIfnJ_YGo",
     "isOekaki": false
    }
   ],
   "sodaneCount": 0,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  },
  {
   "id": "01a1ab7b-3abe-73fa-9954-856355972900",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 9,
   "postSequenceNo": 100493768,
   "status": "public",
   "body": ">無料で遊び続けられる代わりに導入の際の必要な個人情報が多めでハードル高い\n>・このご時世でクレジットカードで年齢認証するか\n>・インスタ・フェイスブックを作って自撮り写真+年齢確認書類ルートか\n>・運が良ければ昔のインスタ・フェイスブックアカウントがあればそこからの連携でそのまま\nうげっわりぃ上の発言撤回して止めとくわ\nインスタとかやってねえしクレカの情報は今怖くて出せねえ",
   "createdAt": "2026-11-05T11:32:24.584Z",
   "attachment": null,
   "attachments": [],
   "sodaneCount": 4,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  },
  {
   "id": "01a1ab80-0be2-750b-a525-98fdab39fccf",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 10,
   "postSequenceNo": 100493852,
   "status": "public",
   "body": "ブラウザ使えて画像認識できてゲームの戦略的思考もできるから認証さえ突破すればあいもげでチェス対戦とかさせられそうだよな",
   "createdAt": "2026-11-05T11:37:38.054Z",
   "attachment": null,
   "attachments": [],
   "sodaneCount": 0,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  },
  {
   "id": "01a1ab80-fba1-739b-b8bc-b826fa2ebf41",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 11,
   "postSequenceNo": 100493867,
   "status": "public",
   "body": "あいもげに不要な負荷かけそうだし認証突破は迷惑かけそうだから個別にサーバー立てるとかで……",
   "createdAt": "2026-11-05T11:38:39.427Z",
   "attachment": null,
   "attachments": [],
   "sodaneCount": 6,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  }
 ]
}"""

    const val THREAD_CLOSED: String = """{
 "aiSummary": {
  "text": "AIサービス「Muse」の利用体験や登録方法について議論するスレッドです。登録にはクレジットカードやSNS連携による年齢認証が必要でハードルが高い一方、招待コードでトークンを得られる仕組みがあるようです。ユーザーはMuseに任せてアプリやドキュメントを作成する楽しさを報告し、ブラウザ操作やゲーム戦略思考など高度な機能への期待も語られています。また、生成小説の比率について感想を述べる投稿や、課金後の快適さに関する質問も見られます。",
  "updatedAt": "2026-11-10T10:03:28.361Z",
  "sourcePostSeqs": [
   0,
   1,
   2,
   3,
   4,
   5,
   6,
   7,
   8,
   9,
   10,
   11,
   319,
   630,
   633
  ]
 },
 "aiTitle": {
  "text": "AIサービスMuseの登録方法・招待コード・利用体験",
  "updatedAt": "2026-11-10T10:03:28.361Z",
  "sourcePostSeqs": [
   0,
   1,
   2,
   3,
   4,
   5,
   6,
   7,
   8,
   9,
   10,
   11,
   319,
   630,
   633
  ]
 },
 "id": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
 "replyCount": 1,
 "createdAt": "2026-11-05T11:18:17.867Z",
 "bumpedAt": "2026-11-10T10:41:30.299Z",
 "expiresAt": "2026-11-13T15:18:17.867Z",
 "isPermanent": false,
 "isSage": false,
 "forceDisplayId": false,
 "allowImageReplies": true,
 "threadNgWords": [],
 "threadRelay": null,
 "closedAt": "2026-11-05T12:00:00.000Z",
 "canShowThreadOwner": false,
 "tags": [
  {
   "name": "技術・ガジェット",
   "kind": "fixed",
   "source": "ai"
  },
  {
   "name": "R18",
   "kind": "fixed",
   "source": "op"
  }
 ],
 "posts": [
  {
   "id": "01a1ab6e-57e8-755a-8c24-61da88f0bd4d",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 0,
   "postSequenceNo": 100493594,
   "status": "public",
   "body": "museスレ\nもちもち",
   "createdAt": "2026-11-05T11:18:17.867Z",
   "attachment": {
    "id": "01a1ab6e-2875-7389-9e96-7c5b2aff272c",
    "kind": "image",
    "mime": "image/webp",
    "width": 1024,
    "height": 1024,
    "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00077338ad8.webp",
    "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00077338ad8.webp",
    "ngHash": "_-_Pg4HBw_8",
    "isOekaki": false
   },
   "attachments": [
    {
     "id": "01a1ab6e-2875-7389-9e96-7c5b2aff272c",
     "kind": "image",
     "mime": "image/webp",
     "width": 1024,
     "height": 1024,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00077338ad8.webp",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00077338ad8.webp",
     "ngHash": "_-_Pg4HBw_8",
     "isOekaki": false
    }
   ],
   "sodaneCount": 12,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  },
  {
   "id": "01a1ab71-162f-71c1-92f2-30ad27936212",
   "threadId": "01a1ab6e-57e8-755b-a01c-66792ff13aef",
   "seq": 1,
   "postSequenceNo": 100493641,
   "status": "public",
   "body": "おつ\nいつの間にか前のが落ちてたのね",
   "createdAt": "2026-11-05T11:21:17.654Z",
   "attachment": null,
   "attachments": [],
   "sodaneCount": 0,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "sage": false,
   "showThreadOwner": false,
   "poll": null
  }
 ]
}"""

    const val ARCHIVE_LIST: String = """{
 "threads": [
  {
   "threadId": "01a19b5d-aba5-7514-8fdb-dab25b35eb3b",
   "opExcerpt": "遊戯王女キャラ・モンスタースレ\nスレ画は適当",
   "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00072893acw.webp",
   "opPreviewUrl": null,
   "replyCount": 67,
   "tags": [
    {
     "name": "アニメ・漫画",
     "kind": "fixed",
     "source": "ai"
    },
    {
     "name": "R18",
     "kind": "fixed",
     "source": "op"
    }
   ],
   "expiresAt": "2026-11-10T09:46:09.723Z",
   "isPermanent": false,
   "archivedAt": "2026-11-10T09:46:30.975Z"
  },
  {
   "threadId": "01a1af3c-b021-74c8-a60d-495fcafd8448",
   "opExcerpt": "こういうシーンさ…\nいいよね",
   "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00078266aNH.webp",
   "opPreviewUrl": null,
   "replyCount": 42,
   "tags": [
    {
     "name": "創作・作品投稿",
     "kind": "fixed",
     "source": "ai"
    },
    {
     "name": "R18",
     "kind": "fixed",
     "source": "op"
    }
   ],
   "expiresAt": "2026-11-10T09:42:32.518Z",
   "isPermanent": false,
   "archivedAt": "2026-11-10T09:42:50.791Z"
  },
  {
   "threadId": "01a1ae33-1ebb-72cf-a9e5-4f77962b0a4b",
   "opExcerpt": "ピンクの幼馴染でシコる夜",
   "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/10/00068110aMa.webp",
   "opPreviewUrl": null,
   "replyCount": 157,
   "tags": [
    {
     "name": "創作・作品投稿",
     "kind": "fixed",
     "source": "ai"
    },
    {
     "name": "R18",
     "kind": "fixed",
     "source": "op"
    }
   ],
   "expiresAt": "2026-11-10T08:52:28.253Z",
   "isPermanent": false,
   "archivedAt": "2026-11-10T08:52:30.947Z"
  }
 ],
 "pagination": {
  "page": 1,
  "limit": 20,
  "total": 1315,
  "totalPages": 66
 },
 "storage": {
  "usedBytes": 56220667171,
  "limitBytes": 268435456000,
  "remainingBytes": 212214788829,
  "usageRatio": 0.2094383059851825
 }
}"""

    const val SNAPSHOT: String = """{
 "schemaVersion": 1,
 "revision": "07225ae426de762aa091b758d0f885b6357d271467e4af7758501dac5b9da883",
 "canonicalUrl": "https://nijiurachan.net/b/ai2/thread/01a19b5d-aba5-7514-8fdb-dab25b35eb3b",
 "board": {
  "slug": "ai2"
 },
 "thread": {
  "id": "01a19b5d-aba5-7514-8fdb-dab25b35eb3b",
  "createdAt": "2026-11-02T08:26:09.723Z",
  "archivedAt": "2026-11-10T09:46:30.975Z",
  "isPermanent": false,
  "tags": [
   {
    "name": "アニメ・漫画",
    "kind": "fixed",
    "source": "ai"
   },
   {
    "name": "R18",
    "kind": "fixed",
    "source": "op"
   }
  ],
  "publicReplyCount": 67,
  "threadNgWords": [],
  "threadRelay": null,
  "aiSummary": {
   "text": "遊戯王の女性キャラクターやモンスターをテーマにしたスレッドで、アキや杏子、明日香などのキャラクターのイラストが投稿され、その魅力について語られています。トリガーレスでは、DSODの杏子のシーンが寝取られものに見えるという意見に触れ、表の遊戯王を寝取られる妄想イラストが添付されています。",
   "updatedAt": "2026-11-04T15:43:44.028Z",
   "sourcePostSeqs": [
    0,
    1,
    2,
    3,
    4,
    5,
    6,
    7,
    8,
    9,
    10,
    11,
    39,
    66,
    67
   ]
  },
  "aiTitle": {
   "text": "遊戯王女キャラスレでDSOD杏子の寝取られ妄想イラストが投稿される",
   "updatedAt": "2026-11-04T15:43:44.028Z",
   "sourcePostSeqs": [
    0,
    1,
    2,
    3,
    4,
    5,
    6,
    7,
    8,
    9,
    10,
    11,
    39,
    66,
    67
   ]
  }
 },
 "posts": [
  {
   "id": "01a19b5d-aba5-7513-abd5-1ebdf1b41e43",
   "seq": 0,
   "postSequenceNo": 100467318,
   "createdAt": "2026-11-02T08:26:09.723Z",
   "body": "遊戯王女キャラ・モンスタースレ\nスレ画は適当",
   "plainText": "遊戯王女キャラ・モンスタースレ\nスレ画は適当",
   "sage": false,
   "showThreadOwner": false,
   "sodaneCount": 4,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "poll": null,
   "attachments": [
    {
     "id": "01a19b5d-7aee-76b1-b2d0-b2611efc5176",
     "kind": "image",
     "mime": "image/jpeg",
     "width": 736,
     "height": 1408,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00072893acw.jpg",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00072893acw.webp",
     "ngHash": "59EB-PPDwwM",
     "isOekaki": false
    }
   ]
  },
  {
   "id": "01a19b63-afa4-75bd-94d7-93f27c3616c2",
   "seq": 1,
   "postSequenceNo": 100467341,
   "createdAt": "2026-11-02T08:32:43.958Z",
   "body": "モンスターもオッケーなの助かる",
   "plainText": "モンスターもオッケーなの助かる",
   "sage": false,
   "showThreadOwner": false,
   "sodaneCount": 0,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "poll": null,
   "attachments": []
  },
  {
   "id": "01a19b64-04d2-7364-9565-8490dc3888f4",
   "seq": 2,
   "postSequenceNo": 100467342,
   "createdAt": "2026-11-02T08:33:05.760Z",
   "body": "アキさんいいよね…",
   "plainText": "アキさんいいよね…",
   "sage": false,
   "showThreadOwner": false,
   "sodaneCount": 7,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "poll": null,
   "attachments": [
    {
     "id": "01a19b63-f151-750f-8c57-c6a9b80559a5",
     "kind": "image",
     "mime": "image/png",
     "width": 832,
     "height": 1216,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00072897aEj.png",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00072897aEj.webp",
     "ngHash": "58fZvT0QAAA",
     "isOekaki": false
    }
   ]
  },
  {
   "id": "01a19b64-7f4d-77ee-89f5-908906937ecc",
   "seq": 3,
   "postSequenceNo": 100467344,
   "createdAt": "2026-11-02T08:33:37.131Z",
   "body": "ライスーアキさんスケベすぎてリンクスの立ち絵でもシコらされる",
   "plainText": "ライスーアキさんスケベすぎてリンクスの立ち絵でもシコらされる",
   "sage": false,
   "showThreadOwner": false,
   "sodaneCount": 1,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "poll": null,
   "attachments": []
  },
  {
   "id": "01a19b6c-36d5-742e-a9c8-961ac9749964",
   "seq": 4,
   "postSequenceNo": 100467391,
   "createdAt": "2026-11-02T08:42:02.859Z",
   "body": "ビッチ仕草をさせたくなる",
   "plainText": "ビッチ仕草をさせたくなる",
   "sage": false,
   "showThreadOwner": false,
   "sodaneCount": 6,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "poll": null,
   "attachments": [
    {
     "id": "01a19b6c-2513-7121-b79d-4c754fe64c94",
     "kind": "image",
     "mime": "image/png",
     "width": 832,
     "height": 1216,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00072915ajg.png",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00072915ajg.webp",
     "ngHash": "OBw8PDwYbmY",
     "isOekaki": false
    }
   ]
  },
  {
   "id": "01a19b6e-eee1-705d-bce6-715b12e3da58",
   "seq": 5,
   "postSequenceNo": 100467403,
   "createdAt": "2026-11-02T08:45:01.064Z",
   "body": "アキさんはおっぱい大きくてライダースーツ着てエッチでいい",
   "plainText": "アキさんはおっぱい大きくてライダースーツ着てエッチでいい",
   "sage": false,
   "showThreadOwner": false,
   "sodaneCount": 0,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "poll": null,
   "attachments": []
  },
  {
   "id": "01a19b6f-c270-722f-a146-3a6d1beaea1d",
   "seq": 6,
   "postSequenceNo": 100467407,
   "createdAt": "2026-11-02T08:45:55.198Z",
   "body": "杏子も明日香もアンナもデカパイなのよきかな",
   "plainText": "杏子も明日香もアンナもデカパイなのよきかな",
   "sage": false,
   "showThreadOwner": false,
   "sodaneCount": 1,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "poll": null,
   "attachments": []
  },
  {
   "id": "01a19b78-9edd-72e6-8409-bf8f5ec8593c",
   "seq": 7,
   "postSequenceNo": 100467455,
   "createdAt": "2026-11-02T08:55:35.921Z",
   "body": ">杏子も明日香もアンナもデカパイなのよきかな\nいいよね…",
   "plainText": ">杏子も明日香もアンナもデカパイなのよきかな\nいいよね…",
   "sage": false,
   "showThreadOwner": false,
   "sodaneCount": 4,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "poll": null,
   "attachments": [
    {
     "id": "01a19b78-7d9d-73ff-b183-c1a6241bf28a",
     "kind": "image",
     "mime": "image/png",
     "width": 832,
     "height": 1216,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00072927asD.png",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00072927asD.webp",
     "ngHash": "wAOJsYAf9_8",
     "isOekaki": false
    },
    {
     "id": "01a19b78-7daa-71ab-9f4a-4c15fdfdf010",
     "kind": "image",
     "mime": "image/png",
     "width": 832,
     "height": 1216,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00072927b0q.png",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00072927b0q.webp",
     "ngHash": "AEMefXODd14",
     "isOekaki": false
    },
    {
     "id": "01a19b78-7d8e-7555-8fe3-64e9fb3c283d",
     "kind": "image",
     "mime": "image/png",
     "width": 832,
     "height": 1216,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00072927cjz.png",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00072927cjz.webp",
     "ngHash": "wID8_70YJDw",
     "isOekaki": false
    }
   ]
  },
  {
   "id": "01a19b81-fd74-72e5-98b0-1f0ee8f3e64c",
   "seq": 8,
   "postSequenceNo": 100467524,
   "createdAt": "2026-11-02T09:05:49.955Z",
   "body": "AVのメリッサ割とエッチで好きだったりする\nあとVRAINSのエマさんも",
   "plainText": "AVのメリッサ割とエッチで好きだったりする\nあとVRAINSのエマさんも",
   "sage": false,
   "showThreadOwner": false,
   "sodaneCount": 1,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "poll": null,
   "attachments": []
  },
  {
   "id": "01a19b8a-c79e-70cd-bc85-c59e90e96312",
   "seq": 9,
   "postSequenceNo": 100467588,
   "createdAt": "2026-11-02T09:15:26.035Z",
   "body": ">AVのメリッサ割とエッチで好きだったりする\n>あとVRAINSのエマさんも\nメリッサは近い人は出るって感じだな",
   "plainText": ">AVのメリッサ割とエッチで好きだったりする\n>あとVRAINSのエマさんも\nメリッサは近い人は出るって感じだな",
   "sage": false,
   "showThreadOwner": false,
   "sodaneCount": 2,
   "superSoudaneCount": 0,
   "displayId": null,
   "displayIdSource": null,
   "poll": null,
   "attachments": [
    {
     "id": "01a19b8a-ac1d-71f8-a692-3e60182e481f",
     "kind": "image",
     "mime": "image/png",
     "width": 832,
     "height": 1216,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00072963a6_.png",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00072963a6_.webp",
     "ngHash": "58PBszGxg4M",
     "isOekaki": false
    },
    {
     "id": "01a19b8a-abfa-7388-a8fc-0c0399d85bb7",
     "kind": "image",
     "mime": "image/png",
     "width": 832,
     "height": 1216,
     "originalUrl": "https://storage.nijiurachan.net/aimg/attachments/2026/11/00072963bti.png",
     "thumbnailUrl": "https://storage.nijiurachan.net/aimg/thumbnails/2026/11/00072963bti.webp",
     "ngHash": "PHxgAEB4fj4",
     "isOekaki": false
    }
   ]
  }
 ],
 "artifacts": {
  "htmlUrl": "https://api.nijiurachan.net/archive/ai2/threads/01a19b5d-aba5-7514-8fdb-dab25b35eb3b/snapshot.html",
  "jsonUrl": "https://api.nijiurachan.net/archive/ai2/threads/01a19b5d-aba5-7514-8fdb-dab25b35eb3b/snapshot.json",
  "imagesZipUrl": "https://api.nijiurachan.net/archive/ai2/threads/01a19b5d-aba5-7514-8fdb-dab25b35eb3b/images.zip"
 }
}"""

    const val ERROR_OBJECT: String = """{
 "error": {
  "code": "RATE_LIMITED",
  "message": "しばらく待ってから再度お試しください"
 }
}"""

    const val ERROR_SUCCESS_FALSE: String = """{
 "success": false,
 "message": "Not Found"
}"""

    const val ERROR_PLAIN_HTML: String = """<!DOCTYPE html><html><head><title>502 Bad Gateway</title></head><body>Bad Gateway</body></html>"""
}
