import {useState} from 'react';
import {MessageCircle} from 'lucide-react';
import {useAuth} from '../context/AuthContext';
import {ChatWindow} from './ChatWindow';

interface ContactSellerButtonProps {
    carId: string;
    sellerId: string;
}

export function ContactSellerButton({carId, sellerId}: ContactSellerButtonProps) {
    const {user, isAuthenticated} = useAuth();
    const [open, setOpen] = useState(false);

    // Don't let the seller message themselves
    if (user?.id === sellerId) return null;

    if (!isAuthenticated) {
        return <p className="contact-hint">Log in to contact the seller</p>;
    }

    return (
        <div className="contact-seller">
            {!open ? (
                <button className="contact-btn" onClick={() => setOpen(true)}>
                    <MessageCircle size={18}/> Contact seller
                </button>
            ) : (
                <div className="contact-modal">
                    <div className="contact-modal-header">
                        <span>Chat with seller</span>
                        <button onClick={() => setOpen(false)}>✕</button>
                    </div>
                    <ChatWindow carId={carId} sellerId={sellerId}/>
                </div>
            )}
        </div>
    );
}