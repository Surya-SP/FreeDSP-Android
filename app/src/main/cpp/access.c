#include <jni.h>
#include <errno.h>
#include <linux/usbdevice_fs.h>
#include <sys/ioctl.h>
#include <string.h>
#include <stdio.h>
#include <time.h>
static const char *error_name(int e) {
#define ERR(x) case x: return #x
    switch(e) {
    case 0: return "NONE";
    ERR(EBUSY); ERR(EACCES); ERR(EPERM); ERR(ENODEV); ERR(ENODATA);
    ERR(ENOENT); ERR(EINVAL); ERR(EBADF); ERR(ENOTTY); ERR(EIO); ERR(EINTR);
    ERR(EHOSTUNREACH); ERR(EFAULT);
    default: return "OTHER";
    }
#undef ERR
}
/* Borrow the already-authorized connection FD. Never open/close, reset, or touch another interface.
 * Kotlin validates original cable and HID class; native guard restricts interface 3.
 * Fixed operations only: 0 GETDRIVER, 1 non-forced CLAIMINTERFACE, 2 RELEASEINTERFACE,
 * 3 DISCONNECT_CLAIM only if the bound driver is exactly "usbhid", 4 CONNECT (let kernel drivers rebind). */
JNIEXPORT jobjectArray JNICALL
Java_com_wellmosphere_freedsp_cable_NativeHidAccess_call(JNIEnv *env, jobject self, jint fd, jint iface, jint op) {
    (void)self;
    struct usbdevfs_getdriver driver = {0};
    struct usbdevfs_disconnect_claim takeover = {0};
    struct usbdevfs_ioctl reconnect = {0};
    unsigned int number = 3;
    struct timespec start, finish;
    clock_gettime(CLOCK_MONOTONIC, &start);
    int result = -1, saved_errno = EINVAL;
    if (fd >= 0 && iface == 3) {
        errno = 0;
        switch(op) {
        case 0: driver.interface = number; result = ioctl(fd, USBDEVFS_GETDRIVER, &driver); break;
        case 1: result = ioctl(fd, USBDEVFS_CLAIMINTERFACE, &number); break;
        case 2: result = ioctl(fd, USBDEVFS_RELEASEINTERFACE, &number); break;
        case 3:
            takeover.interface = number;
            takeover.flags = USBDEVFS_DISCONNECT_CLAIM_IF_DRIVER;
            strncpy(takeover.driver, "usbhid", sizeof(takeover.driver) - 1);
            result = ioctl(fd, USBDEVFS_DISCONNECT_CLAIM, &takeover);
            break;
        case 4:
            reconnect.ifno = (int)number;
            reconnect.ioctl_code = USBDEVFS_CONNECT;
            reconnect.data = NULL;
            result = ioctl(fd, USBDEVFS_IOCTL, &reconnect);
            break;
        default: errno = EINVAL; break;
        }
        saved_errno = result < 0 ? errno : 0; /* Capture on this thread before any JNI/library call. */
    }
    clock_gettime(CLOCK_MONOTONIC, &finish);
    driver.driver[sizeof(driver.driver)-1] = '\0';
    char result_text[24], errno_text[24], elapsed[32];
    snprintf(result_text, sizeof(result_text), "%d", result);
    snprintf(errno_text, sizeof(errno_text), "%d", saved_errno);
    long long us = (finish.tv_sec-start.tv_sec)*1000000LL+(finish.tv_nsec-start.tv_nsec)/1000;
    snprintf(elapsed, sizeof(elapsed), "%lld", us);
    const char *values[] = {result_text, errno_text, error_name(saved_errno),
        saved_errno ? strerror(saved_errno) : "Success", result == 0 && op == 0 ? driver.driver : "", elapsed};
    jclass cls = (*env)->FindClass(env, "java/lang/String");
    if (!cls) return NULL;
    jobjectArray array = (*env)->NewObjectArray(env, 6, cls, NULL);
    if (!array) return NULL;
    for (int i=0; i<6; i++) {
        jstring value=(*env)->NewStringUTF(env, values[i]);
        if (!value) return NULL;
        (*env)->SetObjectArrayElement(env, array, i, value);
        (*env)->DeleteLocalRef(env, value);
    }
    return array;
}
