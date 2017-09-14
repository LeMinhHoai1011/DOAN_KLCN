import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, Save, AlertTriangle, FileText, Download } from 'lucide-react';
import { mockDocuments } from '../services/mockData';
import StatusBadge from '../components/StatusBadge';
import clsx from 'clsx';

const DocumentDetail = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const doc = mockDocuments.find(d => d.id === id) || mockDocuments[0]; // fallback
  
  const [formData, setFormData] = useState(doc.ocrData);
  const [classification, setClassification] = useState(doc.aiClassification);

  const handleSave = () => {
    // mock save API call
    alert('Đã lưu thay đổi & xác nhận!');
    navigate('/documents');
  };

  const InputField = ({ label, value, field, confidence }: { label: string, value: string | number, field: string, confidence: number }) => (
    <div className="mb-4">
      <label className="block text-sm font-medium text-slate-700 mb-1 flex justify-between">
        <span>{label}</span>
        {confidence < 90 && (
          <span className="text-xs text-amber-600 flex items-center gap-1"><AlertTriangle size={12}/> Conf: {confidence}%</span>
        )}
      </label>
      <input 
        type="text" 
        value={value}
        onChange={(e) => setFormData({...formData, [field]: e.target.value})}
        className={clsx(
          "w-full border rounded-lg px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-blue-500/20",
          confidence < 90 ? "border-amber-300 bg-amber-50" : "border-slate-300 bg-white focus:border-blue-500"
        )}
      />
    </div>
  );

  return (
    <div className="space-y-4 h-[calc(100vh-8rem)] flex flex-col">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <button onClick={() => navigate(-1)} className="p-2 hover:bg-slate-200 rounded-full transition-colors text-slate-600">
            <ArrowLeft size={20} />
          </button>
          <div>
            <div className="flex items-center gap-3">
              <h1 className="text-2xl font-bold text-slate-800">{doc.id} - {doc.fileName}</h1>
              <StatusBadge status={doc.status} />
            </div>
            <p className="text-slate-500 text-sm mt-1">Đã tải lên vào {doc.date}</p>
          </div>
        </div>
        <div className="flex gap-3">
          <button className="px-4 py-2 flex items-center gap-2 bg-white border border-slate-200 text-slate-700 rounded-lg hover:bg-slate-50 transition-colors">
            <Download size={18} />
            <span>Tải file gốc</span>
          </button>
          <button onClick={handleSave} className="px-4 py-2 flex items-center gap-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors shadow-sm">
            <Save size={18} />
            <span>Lưu & Xác nhận</span>
          </button>
        </div>
      </div>

      {/* Main Content Split View */}
      <div className="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-6 min-h-0">
        
        {/* Left: Document Preview */}
        <div className="bg-slate-800 rounded-2xl flex flex-col overflow-hidden border border-slate-300 shadow-sm relative">
          <div className="absolute top-4 right-4 bg-black/50 text-white px-3 py-1 rounded-full text-xs backdrop-blur-md">
            1 / 1
          </div>
          {/* Mock PDF Viewer Background */}
          <div className="flex-1 flex items-center justify-center p-8 overflow-auto">
            <div className="bg-white w-full max-w-lg aspect-[1/1.4] shadow-2xl relative">
               {/* Mock Bounding Boxes */}
               <div className="absolute top-[10%] left-[10%] w-[40%] h-[4%] border-2 border-blue-500 bg-blue-500/20"></div>
               <div className="absolute top-[15%] left-[10%] w-[30%] h-[3%] border-2 border-blue-500 bg-blue-500/20"></div>
               <div className="absolute bottom-[20%] right-[10%] w-[25%] h-[4%] border-2 border-amber-500 bg-amber-500/20"></div>
               <div className="w-full h-full flex items-center justify-center text-slate-300">
                  <FileText size={64} className="opacity-20" />
                  <span className="absolute mt-24 text-sm opacity-50">Bản xem trước tài liệu</span>
               </div>
            </div>
          </div>
        </div>

        {/* Right: Data Form & AI Classification */}
        <div className="flex flex-col gap-4 overflow-y-auto pr-2">
          
          {/* AI Classification Card */}
          <div className="bg-white rounded-2xl border border-blue-200 shadow-sm overflow-hidden p-5 bg-gradient-to-br from-blue-50 to-white">
            <div className="flex items-start justify-between mb-4">
              <div>
                <h3 className="text-sm font-semibold text-blue-800 uppercase tracking-wider flex items-center gap-2">
                  <span className="w-2 h-2 rounded-full bg-blue-600 animate-pulse"></span>
                  AI Phân Loại
                </h3>
                <p className="text-sm text-slate-600 mt-1">Kết quả dự đoán của mô hình</p>
              </div>
              <div className="text-right">
                <div className={clsx("text-xl font-bold", doc.aiConfidence >= 90 ? "text-emerald-600" : "text-amber-600")}>
                  {doc.aiConfidence}%
                </div>
                <div className="text-xs text-slate-500">Độ chính xác</div>
              </div>
            </div>

            <div className="space-y-3">
               <div>
                  <label className="block text-xs font-medium text-slate-500 mb-1">Loại nghiệp vụ / Chi phí</label>
                  <select 
                    value={classification}
                    onChange={(e) => setClassification(e.target.value)}
                    className="w-full bg-white border border-slate-300 rounded-lg px-3 py-2 text-sm font-medium text-slate-800 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 outline-none"
                  >
                    <option value="Chi phí văn phòng">Chi phí văn phòng</option>
                    <option value="Chi phí vận chuyển">Chi phí vận chuyển</option>
                    <option value="Chi phí tiếp khách">Chi phí tiếp khách</option>
                    <option value="Chi phí nguyên vật liệu">Chi phí nguyên vật liệu</option>
                    <option value="Chi phí dịch vụ">Chi phí dịch vụ</option>
                    <option value="Tài sản">Tài sản</option>
                    <option value="Khác">Khác</option>
                  </select>
               </div>
            </div>
          </div>

          {/* OCR Fields Card */}
          <div className="bg-white rounded-2xl border border-slate-200 shadow-sm p-5 flex-1">
             <div className="flex items-center justify-between mb-6">
                <h3 className="text-lg font-semibold text-slate-800">Dữ liệu trích xuất</h3>
                <span className="text-xs px-2.5 py-1 bg-slate-100 text-slate-600 rounded-md font-medium">12 trường dữ liệu</span>
             </div>

             <div className="space-y-4">
                <InputField label="Mã số thuế" value={formData.taxCode} field="taxCode" confidence={98} />
                <InputField label="Số hóa đơn" value={formData.invoiceNo} field="invoiceNo" confidence={95} />
                <div className="grid grid-cols-2 gap-4">
                   <InputField label="Tổng tiền trước thuế" value={formData.subTotal} field="subTotal" confidence={92} />
                   <InputField label="Tiền thuế VAT" value={formData.vatAmount} field="vatAmount" confidence={89} />
                </div>
                <InputField label="Hình thức thanh toán" value={formData.paymentMethod} field="paymentMethod" confidence={75} />
                <InputField label="Địa chỉ" value={formData.address} field="address" confidence={96} />
                <InputField label="Số điện thoại" value={formData.phone} field="phone" confidence={91} />
             </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DocumentDetail;
