// Jenkins Shared Library method: goneDevopsExportOfflineImage
// Location: gone-devops-shared/vars/goneDevopsExportOfflineImage.groovy
//
// This file should be created in the gone-devops-shared repository
// (separate from the main codebase).
//
// 上传方式：使用 Jenkins 的 Aliyun OSS Uploader 插件直接上传到 OSS，
// 插件返回离线包的 OSS 访问 URL，回调平台时回传 ossUrl 即可，平台无需联动 infra 模块。
//
// Implementation:
// 1. Validate imageName:imageTag exists (docker images -q)
// 2. docker save ${imageName}:${imageTag} -o /tmp/${sanitized-filename}.tar
// 3. Calculate tar size + SHA256 (sha256sum)
// 4. 使用 Aliyun OSS Uploader 插件上传到 OSS，得到 ossUrl
// 5. Clean up local tar file
// 6. Return JSON metadata string:
//    {"ossUrl": "...", "packageSize": ..., "imageDigest": "...", "imageName": "...", "imageTag": "..."}
//
// Parameters:
// - imageName: String (镜像名称)
// - imageTag: String (镜像标签)
// - ossEndpoint: String (OSS endpoint，如 oss-cn-hangzhou.aliyuncs.com)
// - ossBucket: String (OSS bucket)
// - ossPath: String (OSS 对象路径前缀，如 offline-images/${APP_KEY}/)
// - ossCredentialsId: String (Jenkins 凭据 ID，存储 OSS AccessKey/SecretKey)
//
// Returns: String (JSON metadata)
//
// Example usage in Jenkinsfile:
// env.OFFLINE_IMAGE_PACKAGE_METADATA = goneDevopsExportOfflineImage(
//     imageName: 'myapp',
//     imageTag: 'v1.0',
//     ossEndpoint: 'oss-cn-hangzhou.aliyuncs.com',
//     ossBucket: 'my-bucket',
//     ossPath: 'offline-images/myapp/',
//     ossCredentialsId: 'alicloud-oss-credentials'
// )

def call(Map config) {
    def imageName = config.imageName
    def imageTag = config.imageTag
    def ossEndpoint = config.ossEndpoint
    def ossBucket = config.ossBucket
    def ossPath = config.ossPath
    def ossCredentialsId = config.ossCredentialsId

    // Validate image exists
    def imageId = sh(script: "docker images -q ${imageName}:${imageTag}", returnStdout: true).trim()
    if (!imageId) {
        error("Image ${imageName}:${imageTag} not found")
    }

    // Sanitize filename
    def sanitizedName = "${imageName.replaceAll('[^a-zA-Z0-9_-]', '_')}_${imageTag.replaceAll('[^a-zA-Z0-9_.-]', '_')}"
    def tarFile = "/tmp/${sanitizedName}.tar"
    def ossObjectPath = "${ossPath}${sanitizedName}.tar"

    try {
        // docker save
        sh "docker save ${imageName}:${imageTag} -o ${tarFile}"

        // Calculate size and digest
        def packageSize = sh(script: "stat -f%z ${tarFile} 2>/dev/null || stat -c%s ${tarFile}", returnStdout: true).trim()
        def imageDigest = sh(script: "sha256sum ${tarFile} | awk '{print \$1}'", returnStdout: true).trim()

        // Upload to OSS via Aliyun OSS Uploader 插件，返回公共访问 URL
        def ossUrl = ""
        withCredentials([usernamePassword(credentialsId: ossCredentialsId,
                usernameVariable: 'OSS_ACCESS_KEY_ID',
                passwordVariable: 'OSS_ACCESS_KEY_SECRET')]) {
            ossUrl = aliyunOSSUpload(
                endpoint: ossEndpoint,
                bucket: ossBucket,
                objectPath: ossObjectPath,
                localPath: tarFile,
                accessKeyId: env.OSS_ACCESS_KEY_ID,
                accessKeySecret: env.OSS_ACCESS_KEY_SECRET,
                publicRead: true
            )
        }

        // Return metadata as JSON string
        def metadata = [
            ossUrl: ossUrl,
            packageSize: packageSize.toLong(),
            imageDigest: imageDigest,
            imageName: imageName,
            imageTag: imageTag
        ]
        return groovy.json.JsonOutput.toJson(metadata)

    } finally {
        // Clean up
        sh "rm -f ${tarFile}"
    }
}
