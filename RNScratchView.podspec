
require 'json'

package = JSON.parse(File.read(File.join(__dir__, 'package.json')))

Pod::Spec.new do |s|
  s.name         = "RNScratchView"
  s.version      = "1.0.0"
  s.summary      = "RNScratchView"
  s.homepage     = "https://github.com/EliasMae/react-native-scratch-view"
  s.license      = "MIT"
  s.author       = package['author']
  s.platform     = :ios, "8.0"
  s.source       = { :git => "https://github.com/EliasMae/react-native-scratch-view.git"}
  s.source_files  = "ios/*.{h,m,mm}"
  s.requires_arc = true


  s.dependency "React"

end

  