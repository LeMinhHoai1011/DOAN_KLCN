import { useState } from 'react';
import { UploadCloud, FileType, CheckCircle2, Loader2, Play } from 'lucide-react';
import clsx from 'clsx';
import { useNavigate } from 'react-router-dom';

const Upload = () => {
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [processingState, setProcessingState] = useState<'idle' | 'uploading' | 'processing' | 'completed' | 'error'>('idle');
  const [progress, setProgress] = useState(0);

  const workflowSteps = [
    { id: 1, name: 'Tải lên hệ thống', status: processingState === 'idle' ? 'pending' : (progress > 20 ? 'completed' : 'processing') },
    { id: 2, name: 'Tiền xử lý hình ảnh', status: progress > 20 ? (progress > 40 ? 'completed' : 'processing') : 'pending' },
    { id: 3, name: 'Nhận dạng OCR', status: progress > 40 ? (progress > 60 ? 'completed' : 'processing') : 'pending' },
    { id: 4, name: 'Trích xuất trường dữ liệu', status: progress > 60 ? (progress > 80 ? 'completed' : 'processing') : 'pending' },
    { id: 5, name: 'AI Phân loại', status: progress > 80 ? (progress >= 100 ? 'completed' : 'processing') : 'pending' },
  ];

  const handleSimulateProcess = () => {
    if (!file) return;
    setProcessingState('processing');
    setProgress(0);
    
    // Simulate steps
    const interval = setInterval(() => {
      setProgress(prev => {
        if (prev >= 100) {
          clearInterval(interval);
          setProcessingState('completed');
          // Navigate to details after 1 second
          setTimeout(() => navigate('/documents/DOC-001'), 1500);
          return 100;
        }
        return prev + 10;
      });
    }, 500);
  };

  return (
    <div className="max-w-4xl mx-auto space-y-6">
      <div>
        <h1 className="text-2xl font-bold text-slate-800">Upload Chứng Từ</h1>
        <p className="text-slate-500 mt-1">Kéo thả file vào đây để hệ thống tự động xử lý và trích xuất dữ liệu</p>
      </div>

      <div className="bg-white p-8 rounded-2xl border border-slate-200 shadow-sm">
        
        {processingState === 'idle' ? (
          <div className="border-2 border-dashed border-blue-300 bg-blue-50/50 hover:bg-blue-50 transition-colors rounded-xl p-12 flex flex-col items-center justify-center text-center cursor-pointer relative"
               onClick={() => {
                  // Simulate file selection
                  setFile(new File([""], "invoice_example.pdf", { type: "application/pdf" }));
               }}>
            <div className="w-16 h-16 bg-white rounded-full flex items-center justify-center shadow-sm mb-4">
              <UploadCloud className="text-blue-500" size={32} />
            </div>
            <h3 className="text-lg font-semibold text-slate-800 mb-1">Kéo thả chứng từ vào đây</h3>
            <p className="text-sm text-slate-500 mb-6">Hỗ trợ các định dạng: PDF, JPG, PNG, XML (tối đa 10MB)</p>
            
            <button className="px-6 py-2.5 bg-blue-600 text-white rounded-lg font-medium hover:bg-blue-700 shadow-sm transition-all">
              Chọn File
            </button>
            <input type="file" className="hidden" />
          </div>
        ) : (
          <div className="flex flex-col items-center">
            <div className="w-full flex items-center justify-between bg-slate-50 border border-slate-200 p-4 rounded-xl mb-8">
              <div className="flex items-center gap-3">
                <div className="p-2 bg-blue-100 text-blue-600 rounded-lg">
                  <FileType size={24} />
                </div>
                <div className="text-left">
                  <div className="font-medium text-slate-800">{file?.name || 'document.pdf'}</div>
                  <div className="text-xs text-slate-500">2.4 MB</div>
                </div>
              </div>
              {processingState === 'completed' && <CheckCircle2 className="text-emerald-500" size={24} />}
            </div>

            <div className="w-full max-w-lg space-y-6">
              <h3 className="text-sm font-semibold text-slate-800 uppercase tracking-wider mb-4">Tiến trình xử lý</h3>
              
              <div className="relative">
                <div className="absolute left-3.5 top-2 bottom-2 w-0.5 bg-slate-200"></div>
                <div className="absolute left-3.5 top-2 w-0.5 bg-blue-500 transition-all duration-300" style={{ height: `${progress}%` }}></div>
                
                <div className="space-y-6">
                  {workflowSteps.map((step) => (
                    <div key={step.id} className="relative flex items-center gap-4 z-10">
                      <div className={clsx(
                        "w-7 h-7 rounded-full flex items-center justify-center border-2 bg-white transition-colors duration-300",
                        step.status === 'completed' ? "border-blue-500 text-blue-500" :
                        step.status === 'processing' ? "border-blue-500 text-blue-500 shadow-[0_0_0_4px_rgba(59,130,246,0.1)]" :
                        "border-slate-300 text-slate-300"
                      )}>
                        {step.status === 'completed' ? <CheckCircle2 size={16} /> : 
                         step.status === 'processing' ? <Loader2 size={14} className="animate-spin" /> : 
                         <div className="w-2 h-2 rounded-full bg-slate-300"></div>}
                      </div>
                      <div className={clsx(
                        "font-medium transition-colors",
                        step.status === 'completed' ? "text-slate-800" :
                        step.status === 'processing' ? "text-blue-600" :
                        "text-slate-400"
                      )}>
                        {step.name}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {file && processingState === 'idle' && (
        <div className="flex justify-end gap-3">
          <button onClick={() => setFile(null)} className="px-5 py-2.5 bg-white border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 transition-colors">
            Hủy
          </button>
          <button onClick={handleSimulateProcess} className="px-5 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 shadow-sm transition-colors flex items-center gap-2 font-medium">
            <Play size={18} /> Bắt đầu xử lý
          </button>
        </div>
      )}
    </div>
  );
};

export default Upload;
