#import <AppKit/AppKit.h>
#import <UserNotifications/UserNotifications.h>
#import <Speech/Speech.h>
#import <AVFoundation/AVFoundation.h>

// All AppKit and operation state is confined to the Cocoa main thread.
static NSMutableDictionary *operations;
static NSMutableArray *links;
static NSMutableDictionary *owners;
static NSDictionary *ok(void) { return @{ @"status": @"done" }; }
static NSDictionary *failure(NSString *message) { return @{ @"status": @"error", @"message": message ?: @"処理に失敗しました" }; }
static NSString *string(NSDictionary *data, NSString *key) { return [data[key] isKindOfClass:NSString.class] ? data[key] : @""; }
static void finish(NSString *identifier, NSDictionary *result) {
    if (operations[identifier]) operations[identifier] = result;
    [owners removeObjectForKey:identifier];
}
static BOOL bundled(void) { return [NSBundle.mainBundle.bundleIdentifier isEqualToString:@"com.valoser.futacha.desktop"]; }

// Closing an AppKit sheet can briefly leave Java's window neither key nor main.
static NSWindow *hostWindow(void) {
    NSWindow *window = NSApp.keyWindow ?: NSApp.mainWindow;
    if (window && ![window isKindOfClass:NSOpenPanel.class] && window.contentView) return window;
    for (NSWindow *candidate in NSApp.orderedWindows) {
        if (candidate.isVisible && candidate.contentView && candidate.canBecomeKeyWindow &&
            ![candidate isKindOfClass:NSOpenPanel.class]) return candidate;
    }
    for (NSWindow *candidate in NSApp.windows) {
        if (candidate.isVisible && candidate.contentView && candidate.canBecomeKeyWindow &&
            ![candidate isKindOfClass:NSOpenPanel.class]) return candidate;
    }
    return nil;
}

@interface FutachaNotifications : NSObject <UNUserNotificationCenterDelegate>
@end
@implementation FutachaNotifications
- (void)userNotificationCenter:(UNUserNotificationCenter *)center willPresentNotification:(UNNotification *)notification withCompletionHandler:(void (^)(UNNotificationPresentationOptions))completion {
    completion(UNNotificationPresentationOptionBanner | UNNotificationPresentationOptionSound | UNNotificationPresentationOptionList);
}
- (void)userNotificationCenter:(UNUserNotificationCenter *)center didReceiveNotificationResponse:(UNNotificationResponse *)response withCompletionHandler:(void (^)(void))completion {
    NSString *url = response.notification.request.content.userInfo[@"url"];
    dispatch_async(dispatch_get_main_queue(), ^{
        if ([url isKindOfClass:NSString.class] && url.length && links.count < 64) [links addObject:url];
        [NSApp activateIgnoringOtherApps:YES];
    });
    completion();
}
@end
static FutachaNotifications *notifications;
static UNUserNotificationCenter *center(void) {
    UNUserNotificationCenter *value = UNUserNotificationCenter.currentNotificationCenter;
    if (!notifications) notifications = [FutachaNotifications new];
    value.delegate = notifications;
    return value;
}

@interface FutachaShare : NSObject <NSSharingServicePickerDelegate, NSSharingServiceDelegate>
@property NSString *identifier;
@property NSSharingServicePicker *picker;
@end
@implementation FutachaShare
- (id<NSSharingServiceDelegate>)sharingServicePicker:(NSSharingServicePicker *)picker delegateForSharingService:(NSSharingService *)service { return self; }
- (void)sharingServicePicker:(NSSharingServicePicker *)picker didChooseSharingService:(NSSharingService *)service {
    if (!service) finish(self.identifier, @{ @"status": @"cancelled" });
}
- (void)sharingService:(NSSharingService *)service didShareItems:(NSArray *)items { finish(self.identifier, ok()); }
- (void)sharingService:(NSSharingService *)service didFailToShareItems:(NSArray *)items error:(NSError *)error { finish(self.identifier, failure(error.localizedDescription)); }
@end

@interface FutachaSpeech : NSObject
@property NSString *identifier;
@property SFSpeechRecognizer *recognizer;
@property AVAudioEngine *engine;
@property SFSpeechAudioBufferRecognitionRequest *request;
@property SFSpeechRecognitionTask *task;
@property BOOL tapped;
@property BOOL ending;
@property NSString *text;
- (void)begin;
- (void)stop:(BOOL)cancel;
@end
@implementation FutachaSpeech
- (BOOL)active { return owners[self.identifier] == self; }
- (void)fail:(NSString *)message {
    if (![self active]) return;
    [self stop:YES];
    finish(self.identifier, failure(message));
}
- (void)begin {
    if (![self active] || self.ending) return;
    SFSpeechRecognizerAuthorizationStatus status = SFSpeechRecognizer.authorizationStatus;
    if (status == SFSpeechRecognizerAuthorizationStatusNotDetermined) {
        [SFSpeechRecognizer requestAuthorization:^(SFSpeechRecognizerAuthorizationStatus granted) {
            dispatch_async(dispatch_get_main_queue(), ^{ [self begin]; });
        }];
        return;
    }
    if (status != SFSpeechRecognizerAuthorizationStatusAuthorized) { [self fail:@"システム設定の「プライバシーとセキュリティ」から音声認識を許可してください"]; return; }
    AVAuthorizationStatus audio = [AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeAudio];
    if (audio == AVAuthorizationStatusNotDetermined) {
        [AVCaptureDevice requestAccessForMediaType:AVMediaTypeAudio completionHandler:^(BOOL granted) {
            dispatch_async(dispatch_get_main_queue(), ^{ [self begin]; });
        }];
        return;
    }
    if (audio != AVAuthorizationStatusAuthorized) { [self fail:@"システム設定の「プライバシーとセキュリティ」からマイクを許可してください"]; return; }
    @try {
        self.recognizer = [[SFSpeechRecognizer alloc] initWithLocale:[NSLocale localeWithLocaleIdentifier:@"ja-JP"]];
        if (!self.recognizer.isAvailable) { [self fail:@"音声認識を利用できません。接続と音声入力の設定を確認してください"]; return; }
        self.engine = [AVAudioEngine new];
        self.request = [SFSpeechAudioBufferRecognitionRequest new];
        self.request.shouldReportPartialResults = YES;
        self.request.requiresOnDeviceRecognition = self.recognizer.supportsOnDeviceRecognition;
        AVAudioInputNode *input = self.engine.inputNode;
        AVAudioFormat *format = [input outputFormatForBus:0];
        if (format.sampleRate <= 0 || format.channelCount == 0) { [self fail:@"使用できるマイクが見つかりません"]; return; }
        SFSpeechAudioBufferRecognitionRequest *request = self.request;
        [input installTapOnBus:0 bufferSize:1024 format:format block:^(AVAudioPCMBuffer *buffer, AVAudioTime *when) { [request appendAudioPCMBuffer:buffer]; }];
        self.tapped = YES;
        self.task = [self.recognizer recognitionTaskWithRequest:self.request resultHandler:^(SFSpeechRecognitionResult *result, NSError *error) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (![self active]) return;
                if (result) self.text = result.bestTranscription.formattedString;
                if (result.isFinal || error) {
                    NSString *text = self.text ?: @"";
                    [self stop:YES];
                    finish(self.identifier, text.length ? @{ @"status": @"done", @"text": text } : failure(error.localizedDescription ?: @"音声を認識できませんでした"));
                } else operations[self.identifier] = @{ @"status": @"recording", @"text": self.text ?: @"" };
            });
        }];
        [self.engine prepare];
        NSError *error = nil;
        if (![self.engine startAndReturnError:&error]) { [self fail:error.localizedDescription]; return; }
        operations[self.identifier] = @{ @"status": @"recording", @"text": @"" };
        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 60 * NSEC_PER_SEC), dispatch_get_main_queue(), ^{ if ([self active]) [self stop:NO]; });
    } @catch (NSException *error) { [self fail:error.reason]; }
}
- (void)stop:(BOOL)cancel {
    [self.engine stop];
    if (self.tapped) { [self.engine.inputNode removeTapOnBus:0]; self.tapped = NO; }
    [self.request endAudio];
    if (cancel) { [self.task cancel]; self.task = nil; self.engine = nil; self.request = nil; return; }
    if (self.ending) return;
    self.ending = YES;
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, 5 * NSEC_PER_SEC), dispatch_get_main_queue(), ^{
        if (![self active]) return;
        NSString *text = self.text ?: @"";
        [self stop:YES];
        finish(self.identifier, text.length ? @{ @"status": @"done", @"text": text } : failure(@"音声を認識できませんでした"));
    });
}
@end

static NSArray *shareItems(NSDictionary *request) {
    NSString *path = string(request, @"path");
    if (path.length) return @[[NSURL fileURLWithPath:path]];
    NSString *text = string(request, @"text");
    NSURL *url = [NSURL URLWithString:text];
    if ([url.scheme isEqual:@"https"] || [url.scheme isEqual:@"http"]) return @[url];
    return @[text];
}

static NSDictionary *perform(NSDictionary *request) {
    if (!operations) { operations = [NSMutableDictionary new]; owners = [NSMutableDictionary new]; links = [NSMutableArray new]; }
    NSString *op = string(request, @"op");
    NSString *identifier = string(request, @"id");
    if ([op isEqual:@"poll"]) return operations[identifier] ?: @{ @"status": @"cancelled" };
    if ([op isEqual:@"cancel"]) {
        id owner = owners[identifier];
        if ([owner isKindOfClass:FutachaSpeech.class]) [(FutachaSpeech *)owner stop:YES];
        if ([owner isKindOfClass:NSOpenPanel.class]) [owner cancel:nil];
        if ([owner isKindOfClass:FutachaShare.class]) [[owner picker] close];
        [owners removeObjectForKey:identifier]; [operations removeObjectForKey:identifier];
        return ok();
    }
    if ([op isEqual:@"events"]) { NSArray *result = [links copy]; [links removeAllObjects]; return @{ @"status": @"done", @"urls": result }; }
    if ([op isEqual:@"capabilities"]) return @{ @"status": @"done", @"bundle": NSBundle.mainBundle.bundleIdentifier ?: @"", @"notifications": @(bundled()), @"speechAuthorization": @(SFSpeechRecognizer.authorizationStatus), @"microphoneAuthorization": @([AVCaptureDevice authorizationStatusForMediaType:AVMediaTypeAudio]), @"shareServices": @([NSSharingService sharingServicesForItems:shareItems(@{ @"text": @"https://example.com" })].count) };
    if ([op isEqual:@"dockIcon"]) {
        NSImage *image = [[NSImage alloc] initWithContentsOfFile:string(request, @"path")];
        if (!image) return failure(@"アイコンを読み込めません");
        NSApp.applicationIconImage = image;
        return ok();
    }
    if ([op isEqual:@"notificationSettings"]) {
        NSString *value = @"x-apple.systempreferences:com.apple.Notifications-Settings.extension?id=com.valoser.futacha.desktop";
        BOOL opened = [NSWorkspace.sharedWorkspace openURL:[NSURL URLWithString:value]];
        return opened ? ok() : failure(@"通知設定を開けませんでした");
    }
    if (!identifier.length || operations.count >= 64) return failure(@"別の操作が完了してからお試しください");
    operations[identifier] = @{ @"status": @"pending" };
    if ([op isEqual:@"notificationStatus"] || [op isEqual:@"notificationPermission"] || [op isEqual:@"notify"]) {
        if (!bundled()) { finish(identifier, failure(@"通知はインストールしたMac版で利用できます")); return operations[identifier]; }
        UNUserNotificationCenter *value = center();
        if ([op isEqual:@"notificationPermission"]) {
            [value requestAuthorizationWithOptions:UNAuthorizationOptionAlert | UNAuthorizationOptionSound completionHandler:^(BOOL granted, NSError *error) {
                dispatch_async(dispatch_get_main_queue(), ^{ finish(identifier, error ? failure(error.localizedDescription) : @{ @"status": @"done", @"granted": @(granted) }); });
            }];
        } else [value getNotificationSettingsWithCompletionHandler:^(UNNotificationSettings *settings) {
            dispatch_async(dispatch_get_main_queue(), ^{
                if (!operations[identifier]) return;
                BOOL granted = settings.authorizationStatus == UNAuthorizationStatusAuthorized || settings.authorizationStatus == UNAuthorizationStatusProvisional;
                if ([op isEqual:@"notificationStatus"]) { finish(identifier, @{ @"status": @"done", @"granted": @(granted), @"authorization": @(settings.authorizationStatus) }); return; }
                if (!granted) { finish(identifier, failure(@"通知が許可されていません")); return; }
                UNMutableNotificationContent *content = [UNMutableNotificationContent new];
                content.title = string(request, @"title"); content.body = string(request, @"body");
                content.sound = UNNotificationSound.defaultSound;
                content.userInfo = @{ @"url": string(request, @"url") };
                UNNotificationRequest *note = [UNNotificationRequest requestWithIdentifier:string(request, @"notificationId") content:content trigger:nil];
                [value addNotificationRequest:note withCompletionHandler:^(NSError *error) {
                    dispatch_async(dispatch_get_main_queue(), ^{ finish(identifier, error ? failure(error.localizedDescription) : ok()); });
                }];
            });
        }];
    } else if ([op isEqual:@"share"]) {
        NSWindow *window = hostWindow();
        if (!window) { finish(identifier, failure(@"共有するウィンドウが見つかりません")); return operations[identifier]; }
        [NSApp activateIgnoringOtherApps:YES];
        [window makeKeyAndOrderFront:nil];
        FutachaShare *share = [FutachaShare new]; share.identifier = identifier;
        share.picker = [[NSSharingServicePicker alloc] initWithItems:shareItems(request)]; share.picker.delegate = share;
        owners[identifier] = share;
        [share.picker showRelativeToRect:NSMakeRect(NSMidX(window.contentView.bounds), NSMidY(window.contentView.bounds), 1, 1) ofView:window.contentView preferredEdge:NSRectEdgeMinY];
    } else if ([op isEqual:@"directory"]) {
        NSOpenPanel *panel = [NSOpenPanel openPanel]; panel.canChooseFiles = NO; panel.canChooseDirectories = YES;
        panel.canCreateDirectories = YES; panel.allowsMultipleSelection = NO; panel.message = @"保存先フォルダを選択してください";
        owners[identifier] = panel;
        void (^completion)(NSModalResponse) = ^(NSModalResponse result) { finish(identifier, result == NSModalResponseOK ? @{ @"status": @"done", @"path": panel.URL.path } : @{ @"status": @"cancelled" }); };
        NSWindow *window = hostWindow();
        if (window) [panel beginSheetModalForWindow:window completionHandler:completion]; else [panel beginWithCompletionHandler:completion];
    } else if ([op isEqual:@"speech"]) {
        if (!bundled()) { finish(identifier, failure(@"音声入力はインストールしたMac版で利用できます")); return operations[identifier]; }
        for (id owner in owners.allValues) if ([owner isKindOfClass:FutachaSpeech.class]) { finish(identifier, failure(@"音声入力はすでに実行中です")); return operations[identifier]; }
        FutachaSpeech *speech = [FutachaSpeech new]; speech.identifier = identifier;
        owners[identifier] = speech; [speech begin];
    } else if ([op isEqual:@"speechStop"]) {
        // Handled before allocation by the exported dispatcher.
    } else { finish(identifier, failure(@"不明な操作です")); }
    return operations[identifier];
}

__attribute__((visibility("default"))) char *futacha_mac_call(const char *json) {
    @autoreleasepool {
        __block NSDictionary *result;
        NSData *data = json ? [[NSString stringWithUTF8String:json] dataUsingEncoding:NSUTF8StringEncoding] : nil;
        NSDictionary *request = data ? [NSJSONSerialization JSONObjectWithData:data options:0 error:nil] : nil;
        if (![request isKindOfClass:NSDictionary.class]) return strdup("{\"status\":\"error\",\"message\":\"Invalid request\"}");
        void (^call)(void) = ^{
            @try {
                if ([string(request, @"op") isEqual:@"speechStop"]) {
                    FutachaSpeech *speech = owners[string(request, @"id")];
                    if ([speech isKindOfClass:FutachaSpeech.class]) [speech stop:NO];
                    result = ok();
                }
                else result = perform(request);
            } @catch (NSException *error) { result = failure(error.reason); }
        };
        if (NSThread.isMainThread) call(); else dispatch_sync(dispatch_get_main_queue(), call);
        NSData *encoded = [NSJSONSerialization dataWithJSONObject:result ?: ok() options:0 error:nil];
        return strdup([[NSString alloc] initWithData:encoded encoding:NSUTF8StringEncoding].UTF8String);
    }
}
__attribute__((visibility("default"))) void futacha_mac_free(char *value) { free(value); }
